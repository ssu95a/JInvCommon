package ru.inversion.email.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.inversion.email.EmailException;
import ru.inversion.email.EmailService;
import ru.inversion.email.IEmail;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;
import ru.inversion.utils.converter.TypeConverter;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.ws.rs.core.MediaType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** */
public class EmailServiceImpl {

    private static abstract class BaseDataSource implements DataSource {

        private IEmail.IItem item;
        /** */
        public BaseDataSource(IEmail.IItem item) {
            this.item = item;
        }
        /** */
        public OutputStream getOutputStream() throws IOException {
            throw new IOException("This DataHandler cannot write");
        }
        /** */
        public String getContentType() {
            return item.getMimeType();
        }
        /** */
        public String getName() {
            return Tags.PRODUCT_LABEL + "Data source for sending email only";
        }
        /** */
        public IEmail.IItem item() throws IOException {
            if (item.getData() == null) {
                throw new IOException("Null text");
            }
            return item;
        }
    }

    /** */
    private static class TextDataSource extends BaseDataSource {

        public TextDataSource(IEmail.IItem<Object> item) {
            super(item);
        }
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream( item().getData().toString().getBytes(StandardCharsets.UTF_8) );
        }
    }

    /** */
    private static class CLOBDataSource extends BaseDataSource {

        /** */
        public CLOBDataSource(IEmail.IItem<Object> item) {
            super(item);
        }

        /** */
        public InputStream getInputStream() throws IOException {
            try {
                Clob clob = (Clob) item().getData();
                return new ByteArrayInputStream( clob.getSubString(1, (int) clob.length()).getBytes(StandardCharsets.UTF_8) );
            } catch (SQLException ex) {
                throw new IOException("getInputStream", ex);
            }
        }
    }

    /** */
    private static class StreamDataSource extends BaseDataSource {
        /** */
        public StreamDataSource(IEmail.IItem<Object> item) {
            super(item);
        }
        /** */
        public InputStream getInputStream() throws IOException {
            return (InputStream) item().getData();
        }
    }

    final private static Logger         logger = LoggerFactory.getLogger( "ru.inversion.mail" );
    final private static ResourceBundle bundle = ResourceBundle.getBundle("fore");

    private static volatile EmailServiceImpl instance;

    public static EmailServiceImpl instance() {

        if( instance == null )
        {
            synchronized( EmailServiceImpl.class ) {
                if( instance == null )
                    instance = new EmailServiceImpl();
            }
        }
        return instance;
    }

    /** */
    private EmailServiceImpl( ) {
        logger.info("@ Start e-mail service");
    }

    /** */
    private Session getSession( Function<String,Object> parameters ) throws EmailException {

        try {

            Properties properties = new Properties();

            String host = (String)parameters.apply(EmailService.SMTP_HOST);

            if( S.isNullOrEmpty(host) )
                throw new IllegalStateException( Tags.PRODUCT_LABEL + bundle.getString("NE_NASTROEN_SERVER_ISKHODYASHCHEJ_POCHTY_SMTP") );

            String port = (String)parameters.apply(EmailService.SMTP_PORT);

            if( !S.isNullOrEmpty(port) )
            {
                if( host.indexOf(';') == -1 )
                    host = host + ';' + port;
            }

            if( host.indexOf(';') == -1 )
                properties.put("mail.smtp.host", host);
            else
            {
                int i = 0;

                for( String s : host.split(";") )
                {
                    switch( i++ )
                    {
                        case 0: properties.put("mail.smtp.host", s); break;
                        case 1: properties.put("mail.smtp.port", s); break;
                        default:
                            break;
                    }
                }//end for
            }

            boolean debug = U.nvl( TypeConverter.convert( parameters.apply(EmailService.SMTP_DEBUG), Boolean.class ), Boolean.FALSE );

            if( debug )
                properties.put( "mail.debug", "true" );

            Authenticator auth = null;

            final String login = (String)parameters.apply(EmailService.SMTP_LOGIN);

            if( !S.isNullOrEmpty(login) )
            {
                properties.put( "mail.smtp.auth", true );

                final String password = (String)parameters.apply(EmailService.SMTP_PASSWORD);

                auth = new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication( login, password );
                    }
                };
            }

            Session session = Session.getInstance( properties, auth );

//            if( debug )
//                session.setDebug( true );

            return session;

        } catch( Throwable ex ) {
            throw new EmailException( bundle.getString("NE_VOZMOZHNO_POLUCHIT_SERVER_ISKHODYASHCHEJ_POCHTY_SMTP"), ex );
        }
    }
    /** */
    private void addTextAttach(MimeBodyPart mbp, IEmail.IItem<Object> item) throws MessagingException, IOException {
        mbp.setDataHandler( new DataHandler(new TextDataSource(item)) );
        mbp.setFileName   ( item.getName() );
    }

    /** */
    private void addClobAttach(MimeBodyPart mbp, IEmail.IItem<Object> item) throws MessagingException, IOException {
        mbp.setDataHandler( new DataHandler(new CLOBDataSource(item)) );
        mbp.setFileName   ( item.getName() );
    }

    /** */
    private void addStreamAttach(MimeBodyPart mbp, IEmail.IItem<Object> item) throws MessagingException, IOException {
        mbp.setDataHandler( new DataHandler(new StreamDataSource(item)) );
        mbp.setFileName   ( item.getName() );
    }

    /** */
    private void addFileAttach(MimeBodyPart mbp, IEmail.IItem<Object> item) throws MessagingException, IOException {

        final File file = (File) item.getData();

        if( file == null || !file.exists() || !file.isFile())
            throw new FileNotFoundException( file == null ? "null" : file.getAbsolutePath() );

        FileDataSource fds = new FileDataSource(file) {
            public String getContentType() {
                return U.firstNotNull( item.getMimeType(), super.getContentType(), MediaType.APPLICATION_OCTET_STREAM );
            }
        };
        mbp.setDataHandler( new DataHandler(fds) );
        mbp.setFileName   ( U.nvl( item.getName(), file.getName() ) );
    }

    /** */
    final static private BiConsumer<Integer,String> STUB_LISTENER = ( i, s ) -> {};

    /** */
    public void send( IEmail email, Function<String,Object> parameters, BiConsumer<Integer,String> listener ) throws EmailException {

        String errorContent = null;

        try {

            listener = U.nvl( listener, STUB_LISTENER );

            errorContent = "Create and init SMTP session"; listener.accept( 10, errorContent );

            MimeMessage message = new MimeMessage( getSession(parameters) );

            errorContent = "Init message parameters"; listener.accept( 15, errorContent );

            message.setFrom      ( email.getFrom() );
            message.setRecipients( Message.RecipientType.TO, email.getRecipients( IEmail.RecipientTypeEnum.TO ) );
            message.setSubject   ( email.getSubject() );
            message.setSentDate  ( new Date() );

            final List<IEmail.IItem<String>> contentList = email.getContentList();
            final List<IEmail.IItem<Object>> attachList  = email.getAttachList( );

            errorContent = "Set content"; listener.accept( 20, errorContent);

            if( contentList.size() == 1 && attachList == null )
            // Простое письмо, без вложений - его делаем просто и просто отправляем
            {
                final IEmail.IItem< String > item = contentList.get(0);
                message.setContent( item.getData(), item.getMimeType() + "; charset=\"utf-8\"" );
                //message.setText( item.getData(), "utf-8", item.getMimeType() );
            }
            else
            {
                Multipart mmp = new MimeMultipart();

                for( IEmail.IItem< String > item : contentList )
                {
                    MimeBodyPart mbp = new MimeBodyPart();
                    mbp.setContent ( item.getData(), item.getMimeType() + "; charset=\"utf-8\"" );
                    mmp.addBodyPart( mbp );
                }//end for

                errorContent = "Set attachments"; listener.accept( 25, errorContent );

                if( attachList != null )
                {
                    int n = 26;

                    for( IEmail.IItem<Object> item : attachList )
                    {
                        MimeBodyPart mbp = new MimeBodyPart( );

                        if( item.getData() instanceof File )
                        {
                            errorContent = "Add file attach"; listener.accept( n++, errorContent );
                            addFileAttach( mbp, item );
                        }
                        else if (item.getData() instanceof Clob )
                        {
                            errorContent = "Add CLOB attach"; listener.accept( n++, errorContent );
                            addClobAttach( mbp, item);
                        }
                        else if (item.getData() instanceof InputStream )
                        {
                            errorContent = "Add Stream attach"; listener.accept( n++, errorContent );
                            addStreamAttach( mbp, item );
                        }
                        else
                        {
                            errorContent = "Add Text attach"; listener.accept( n++, errorContent );
                            addTextAttach( mbp, item );
                        }

                        mmp.addBodyPart(mbp);

                    }//end for
                }
                message.setContent(mmp);
            }

            errorContent = "Send message"; listener.accept( 50, errorContent );

            Transport.send(message);

            listener.accept( 100, "done.");

        } catch( Throwable th ) {
            logger.error( "Error on send e-mail", th );
            throw new EmailException( Tags.PRODUCT_LABEL + "Error on send e-mail", errorContent, th );
        }
    }
}
