package ru.inversion.db.session.cfg_impl.srv;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;

import ru.inversion.db.session.xxi.XXIConnectorException;
import ru.inversion.utils.Checks;
import ru.inversion.utils.S;
import ru.inversion.utils.dco.Dco;
import ru.inversion.utils.dco.IDco;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import static org.slf4j.LoggerFactory.getLogger;

/** */
public final class XXIConnectClient implements Closeable {

    private static final Logger logger = getLogger(MethodHandles.lookup().lookupClass());

    private static final String HDR_REQUEST_ID = "X-Request-Id";

    private final URI authUri;
    private final CloseableHttpClient httpClient;

    /** */
    private static URI normalizeAuthUri( URI baseUri ) {

        String s = baseUri.toString();

        if( s.endsWith("/auth") )
            return baseUri;

        if( s.endsWith("/auth/") )
            return URI.create( s.substring(0, s.length() - 1) );

        if( s.endsWith("/") )
            return URI.create( s + "auth" );

        return URI.create( s + "/auth" );
    }

    /** */
    private static CloseableHttpClient defaultHttpClient() {

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(5))
                .build();

        BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager();
        connectionManager.setConnectionConfig(connectionConfig);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                .setResponseTimeout(Timeout.ofSeconds(15))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .build();
    }

    public XXIConnectClient(URI baseUri) {
        this(baseUri, defaultHttpClient());
    }

    /** */
    public XXIConnectClient( URI baseUri, CloseableHttpClient httpClient ) {
        this.authUri    = normalizeAuthUri( Checks.Require.object(baseUri,"baseUri" ));
        this.httpClient = Checks.Require.object( httpClient,"httpClient" );
    }

    /** */
    public AuthSuccess authenticate( String alias, String login, char[] password ) throws IOException {
        return authenticate( new AuthRequest(alias, login, password) );
    }

    /** */
    public AuthSuccess authenticate( AuthRequest request ) throws IOException {

        validateRequest( Checks.Require.object(request, "request") );

        final long t0 = System.nanoTime();

        final String requestId = UUID.randomUUID().toString();
        final byte[] xmlBytes  = buildAuthRequestXml(request);

        logger.debug (
            "auth.client.request uri={} alias={} login={} rid={}",
            authUri, request.getAlias(), request.getLogin(), requestId
        );

        try {

            HttpPost post = new HttpPost(authUri);
            post.setHeader("Accept", "application/xml");
            post.setHeader( HDR_REQUEST_ID, requestId );

            ClientParameter.fillHeaders(post::setHeader);

            ByteArrayEntity entity = new ByteArrayEntity( xmlBytes, ContentType.APPLICATION_XML.withCharset(StandardCharsets.UTF_8) );
            post.setEntity(entity);

            final char[] clientPassword = request.getPassword();

            final AuthSuccess result = httpClient.execute(post, response -> {
                int httpStatus = response.getCode();
                byte[] body = readBody(response.getEntity());
                return parseResponse( httpStatus, response.getHeaders(), body, requestId, clientPassword );
            });

            final int msTotal = elapsedMs(t0);

            logger.info (
                    "auth.client.ok uri={} http={} rid={} alias={} login={} ms_total={}",
                    authUri,
                    result.httpStatus(),
                    result.requestId(),
                    request.getAlias(), request.getLogin(),
                    msTotal
            );

            return result;

        } finally {
            Arrays.fill( xmlBytes, (byte)0);
        }
    }


    /** */
    private static int elapsedMs(long startedAtNano) {
        return (int) ((System.nanoTime() - startedAtNano) / 1_000_000L);
    }


    /** */
    @Override
    public void close() throws IOException {
        logger.debug("auth.client.close uri={}", authUri);
        httpClient.close();
    }

    /** */
    private static void validateRequest(AuthRequest request) {

        Checks.Require.text ( request.getAlias(), "alias" );
        Checks.Require.text ( request.getLogin(), "login" );

        Checks.Require.chars( request.getPassword(), "password" );
    }

    /** */
    private static byte[] buildAuthRequestXml(AuthRequest request)
    {
        final IDco dco = new Dco("authRequest");
        dco.a("v").set("1");
        dco.e("target").e("alias").value( request.getAlias() );

        IDco usr = dco.e("user" );
        usr.e("login"   ).set( request.getLogin() );
        usr.e("password").set( String.valueOf( request.getPassword() ) );

        return dco.asXmlBytes(StandardCharsets.UTF_8);
    }


    /** */
    private static byte[] readBody(HttpEntity entity) throws IOException {

        if( entity == null )
            return new byte[0];

        return EntityUtils.toByteArray(entity);
    }


    /** */
    private static AuthSuccess parseResponse(
        int httpStatus,
        Header[] headers,
        byte[] body,
        String clientRequestId,
        char[] clientPassword
    )
        throws IOException
    {
        final IDco responseDco;

        try {
            responseDco = Dco.parseXml(new ByteArrayInputStream(body));
        } catch( Throwable th ) {
            throw new IOException("Invalid XML response from auth service", th);
        }

        final String status = responseDco.single("/authResponse/status")
                .map(IDco::<String>value)
                .orElse(S.EMPTY_STRING);

        final String requestId = responseDco.single("/authResponse/requestId")
                .map(IDco::<String>value)
                .orElse(S.EMPTY_STRING);

        final String responseRequestId =
                S.firstNonBlank(findHeader(headers, HDR_REQUEST_ID), requestId, clientRequestId);

        if( "OK".equalsIgnoreCase(status) )
        {
            return responseDco.single("/authResponse").<AuthSuccess>map(dco -> {

                final String login = dco.single("login" ).map(IDco::<String>value).orElse(S.EMPTY_STRING);
                final String alias = dco.single("alias" ).map(IDco::<String>value).orElse(S.EMPTY_STRING);
                final String dburl = dco.single("url"   ).map(IDco::<String>value).orElse(S.EMPTY_STRING);
                final String vendr = dco.single("vendor").map(IDco::<String>value).orElse(S.EMPTY_STRING);

                String nonce
                    = dco.single("passwordContainer/nonce")
                        .map(IDco::<String>value).orElseThrow(() -> new IllegalArgumentException("Missing nonce"));

                String ciphertext
                    = dco.single("passwordContainer/ciphertext")
                        .map(IDco::<String>value).orElseThrow(() -> new IllegalArgumentException("Missing ciphertext"));

                PasswordTools.PasswordContainer pc =
                        PasswordTools.PasswordContainer.fromBase64( nonce, ciphertext );

                try {

                    char[] dbPassword = PasswordTools.decrypt( clientPassword, login, pc );

                    return new AuthSuccess (
                            httpStatus,
                            responseRequestId,
                            login,
                            pc,
                            dbPassword,
                            alias,
                            dburl, vendr
                    );

                }catch (IllegalStateException ex) {
                    throw new RuntimeException(
                        "Failed to decrypt password container: requestId=" + responseRequestId + ", httpStatus=" + httpStatus + ", loginLength=" + login.length(), ex
                    );
                }
            }).orElseThrow(() -> new IllegalArgumentException("Bad response format"));
        }

        throw XXIConnectorException.fromRemote( httpStatus, responseRequestId, null, null, responseDco );
    }

    /** */
    private static String findHeader(Header[] headers, String name )
    {
        if( headers == null || name == null )
            return null;

        return Arrays.stream(headers).filter(Objects::nonNull).filter(h -> name.equalsIgnoreCase(h.getName()))
                .map(Header::getValue).filter(Objects::nonNull).findFirst().orElse(null);
    }


    public static final class AuthRequest {
        private final String alias;
        private final String login;
        private final char[] password;

        public AuthRequest(String alias, String login, char[] password) {
            this.alias = alias;
            this.login = login;
            this.password = password;
        }

        public String getAlias() {
            return alias;
        }

        public String getLogin() {
            return login;
        }

        public char[] getPassword() {
            return password;
        }
    }

    public static final class AuthSuccess {
        private final int httpStatus;
        private final String requestId;
        private final String dbLogin;
        private final PasswordTools.PasswordContainer passwordContainer;
        private final char[] dbPassword;
        private final String dbAlias;
        private final String dbUrl;
        private final String dbVendor;
        //private final String rawXml;

        public AuthSuccess (
                int httpStatus,
                String requestId,
                String dbLogin,
                PasswordTools.PasswordContainer passwordContainer,
                char[] dbPassword, String dbAlias, String dbUrl, String dbVendor
        )
        {
            this.httpStatus = httpStatus;
            this.requestId = requestId;
            this.dbLogin = dbLogin;
            this.passwordContainer = passwordContainer;
            this.dbPassword = dbPassword;
//            this.rawXml = rawXml;
            this.dbAlias = dbAlias;
            this.dbUrl = dbUrl;
            this.dbVendor = dbVendor;
        }

        public int httpStatus() {
            return httpStatus;
        }

        public String requestId() {
            return requestId;
        }

        public String dbLogin() {
            return dbLogin;
        }

        public PasswordTools.PasswordContainer passwordContainer() {
            return passwordContainer;
        }

        public char[] dbPassword() {
            return dbPassword;
        }

        public String dbAlias() {
            return dbAlias;
        }

        public String dbUrl() {
            return dbUrl;
        }

        public String dbVendor() {
            return dbVendor;
        }
    }

}