package ru.inversion.email.impl;

import ru.inversion.email.EmailException;
import ru.inversion.email.EmailService;
import ru.inversion.email.IEmail;
import ru.inversion.email.IEmailBuilder;
import ru.inversion.utils.S;
import ru.inversion.utils.Tags;
import ru.inversion.utils.U;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** */
public class EmailBuilderImpl implements IEmailBuilder {

    private static class Item<T> implements IEmail.IItem<T> {

        final private T data;
        final private String name;
        private String mimeType;

        /** */
        public Item( T data, String mimeType, String name ) {
            this.data     = data;
            this.mimeType = mimeType;
            this.name     = name;
        }

        /** */
        public Item( T data, String mimeType ) {
            this( data, mimeType, null );
        }
        /** */
        @Override
        public String getName() {
            return name;
        }
        /** */
        @Override
        public String getMimeType() {
            return mimeType;
        }
        /** */
        public void setMimeType( String v ) {
            mimeType = v;
        }
        /** */
        @Override
        public T getData() {
            return data;
        }
    }

    private List<IEmail.IItem<Object>> contentList = null; //new ArrayList<>();
    private List<IEmail.IItem<Object>> attachList  = null; //new LinkedList< Item<Object> >();

    private Object fromAddress = null;
    private String subject     = null;
    private String fromName    = null;

    private List<Object> recipientList = new ArrayList<>();

    private final Function<String,Object> settingsSupplier;

    public EmailBuilderImpl( Function<String,Object> settingsSupplier ) {
        this.settingsSupplier = U.nvl( settingsSupplier, (s)->null );
    }

    @Override
    public IEmailBuilder subject( String subject ) {
        this.subject = subject;
        return this;
    }

    @Override
    public IEmailBuilder from( String fromAddress ) {
        this.fromAddress = S.nullIfEmpty( fromAddress );
        return this;
    }

    @Override
    public IEmailBuilder from( String fromAddress, String fromName ) {
        this.fromAddress = S.nullIfEmpty( fromAddress );
        this.fromName    = S.nullIfEmpty( fromName    );
        return this;
    }

    @Override
    public IEmailBuilder from( InternetAddress fromAddress ) {
        this.fromAddress = fromAddress;
        return this;
    }

    @Override
    public IEmailBuilder recipient( String recipientAddress ) {

        if( !S.isNullOrEmpty(recipientAddress) )
            recipientList.add( recipientAddress );

        return this;
    }

    @Override
    public IEmailBuilder recipient( InternetAddress recipientAddress ) {

        if( recipientAddress != null )
            recipientList.add( recipientAddress );

        return this;
    }

    @Override
    public IEmailBuilder recipients( Iterable< String > recipients ) {

        if( recipients != null )
            recipientList.add( recipients );

        return this;
    }

    @Override
    public IEmailBuilder recipients( Supplier< Iterable<String> > recipientsFactory ) {

        if( recipientsFactory != null )
            recipientList.add( recipientsFactory );

        return this;
    }

    /** */
    private IEmailBuilder addContent( Object data, String mimeType ) {

        if( data != null && !( S.isString(data) && ((String)data).isEmpty() ) )
        {
            if( contentList == null )
                contentList = new ArrayList<>();

            contentList.add( new Item<>( data, mimeType ) );
        }

        return this;
    }

    /** */
    private IEmailBuilder addAttach( String name, Object data, String mimeType ) {

        if( data != null )
        {
            if( attachList == null )
                attachList = new ArrayList<>();

            attachList.add( new Item<>( data, mimeType, name ) );
        }

        return this;
    }

    /** */
    @Override
    public IEmailBuilder content( String text ) {
        return addContent( text, MediaType.TEXT_PLAIN );
    }

    /** */
    @Override
    public IEmailBuilder content( Object data, String mimeType ) {
        return addContent( data, mimeType );
    }

    /** */
    @Override
    public IEmailBuilder attach( String name, Object data, String mimeType ) {
        return addAttach( name, data, mimeType );
    }

    /** */
    @Override
    public IEmailBuilder attach( File file ) {
        return addAttach( null, file, null );
    }

    /** */
    @Override
    public IEmailBuilder attach( String name, File file ) {
        return addAttach( name, file, null );
    }

    /** */
    private void createRecipient( Object r, List<InternetAddress> list ) throws AddressException {

        if( r instanceof InternetAddress )
            list.add((InternetAddress)r);
        else if( r instanceof String )
        {
            for( InternetAddress ia : InternetAddress.parse( (String)r ) )
                 list.add(ia);
        }
        else if ( r instanceof Iterable ) {
            for( Object o : (Iterable)r )
                 createRecipient( o, list );
        }
        else if( r instanceof Supplier ) {
            for( Object o : ((Supplier<Iterable>)r).get() )
                createRecipient( o, list );
        }
    }

    /** */
    private String contentType( Object o ) {

        try {

            if( o == null )
                return null;

            if( o instanceof File )
                return Files.probeContentType( ((File)o).toPath() );

            if( o instanceof Path )
                return Files.probeContentType( (Path)o );
        }
        catch( Throwable ignored ) {
        }

        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /** */
    private List<IEmail.IItem<String>> prepareContent( ) throws Exception {

        List<IEmail.IItem<String>> retList = new ArrayList<>( this.contentList.size() );

        for( IEmail.IItem<Object> item : this.contentList )
        {
             Object data = item.getData();

             if( S.isString(data) )
                 retList.add( new Item<>( (String) data, item.getMimeType() ) );
             else if( data instanceof Reader )
                retList.add( new Item<>( S.toString( (Reader)data ), item.getMimeType() ) );

             else if( data instanceof File )
             {
                 File f = (File)data;

                 if( !f.exists() || !f.isFile() )
                     throw new FileNotFoundException( f.getAbsolutePath() );

                 String mimeType = item.getMimeType();

                 if( S.isNullOrEmpty( mimeType ) )
                     mimeType = contentType(f);

                 retList.add( new Item<>( S.toString( Files.newBufferedReader( f.toPath(), StandardCharsets.UTF_8 ) ), mimeType ) );
             }
             else if( data instanceof Path )
             {
                 Path p = (Path)data;

                 if( !Files.exists(p) || !Files.isRegularFile(p) )
                     throw new FileNotFoundException( p.toString() );

                 String mimeType = item.getMimeType();

                 if( S.isNullOrEmpty( mimeType ) )
                     mimeType = contentType(p);

                 retList.add( new Item<>( S.toString( Files.newBufferedReader( p, StandardCharsets.UTF_8 ) ), mimeType ) );
             }
             else
                 retList.add( new Item<>( data.toString(), item.getMimeType() ) );
        }

        return retList;
    }

    /** */
    private InternetAddress getFromAddress( ) throws AddressException, UnsupportedEncodingException {

        if( fromAddress == null )
            fromAddress = settingsSupplier.apply( EmailService.DEFAULT_FROM_ADDR );

        if( fromAddress != null )
        {
            if( fromAddress instanceof InternetAddress )
                return (InternetAddress)fromAddress;

            final InternetAddress ia = InternetAddress.parse(fromAddress.toString())[0];
            ia.setPersonal( U.nvl( fromName, (String)settingsSupplier.apply( EmailService.DEFAULT_FROM_NAME ) ) );

            return ia;
        }

        return null;
    }

    /** */
    @Override
    public IEmail build( ) throws EmailException {

        String contentErrMessage = null;

        try {

            if( recipientList.isEmpty( ) )
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "Recipient list is empty" );

            if( contentList == null )
                throw new IllegalStateException( Tags.PRODUCT_LABEL + "Content message is empty");

            contentErrMessage = "Prepare 'From address'";

            final InternetAddress fromAddressIA = getFromAddress( );

            contentErrMessage = "Prepare 'Recipient List'";

            // recipients
            final List<InternetAddress> recipients = new ArrayList<>( recipientList.size() );

            for( Object r : recipientList )
                createRecipient( r, recipients );

            recipientList = null;

            // content
            contentErrMessage = "Prepare 'content message'";

//            final List<IEmail.IItem<String>> content =
//                  ( this.contentList.stream().filter( (o)->!S.isString(o) ).count() == 0L )
//                  ? (List<IEmail.IItem<String>>)(Object)contentList
//                  : prepareContent();

            final boolean allStrings = this.contentList.stream().allMatch( (it) -> S.isString( it.getData() ) );
            final List<IEmail.IItem<String>> content = allStrings
                    ? this.contentList.stream().map( (it) -> new Item<>( (String) it.getData(), it.getMimeType() ) ).collect( java.util.stream.Collectors.toList() )
                    : prepareContent();

            contentList = null;

            // attach
            contentErrMessage = "Prepare 'attachments message'";

            if( attachList != null )
                attachList.stream()
                    .filter( (a)->S.isNullOrEmpty( a.getMimeType() ) )
                            .map( (a)->(Item)a )
                                .forEach( (a)->a.setMimeType( contentType( a.getData() ) ) );

            final List<IEmail.IItem<Object>> attachments = ( attachList == null ) ? null : new ArrayList<>( attachList );

            return new IEmail() {
                @Override
                public InternetAddress getFrom() {
                    return fromAddressIA;
                }

                @Override
                public InternetAddress[] getRecipients( RecipientTypeEnum recipientType ) {
                    return recipients.toArray( new InternetAddress[recipients.size()] );
                }

                @Override
                public String getSubject() {
                    return subject;
                }

                @Override
                public List< IItem<String> > getContentList() {
                    return content;
                }

                @Override
                public List< IItem<Object> > getAttachList() {
                    return attachments;
                }
            };
        }
        catch( Throwable th ) {
            throw new EmailException( Tags.PRODUCT_LABEL + "Error on build e-mail message", contentErrMessage, th );
        }
    }

    @Override
    public void send( ) throws EmailException {
        EmailService.send( build(), settingsSupplier, null );
    }

    @Override
    public void send( BiConsumer< Integer, String > listener ) throws EmailException {
        EmailService.send( build(), settingsSupplier, listener );
    }
}
