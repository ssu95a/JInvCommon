package ru.inversion.db.session.cfg_impl;

import ru.inversion.db.session.AbstractDataSourceCfgBuilder;
import ru.inversion.db.session.DataSourceCfg;
import ru.inversion.db.session.cfg_impl.srv.XXIConnectClient;

import java.net.URI;

public class Srv_DataSourceCfgBuilder extends AbstractDataSourceCfgBuilder {

    final private String authServerUrl;

    public Srv_DataSourceCfgBuilder(String appId, String authServerUrl ) {
        super(appId);
        this.authServerUrl = authServerUrl;
    }

    @Override
    public DataSourceCfg build() throws Exception {

        preBuild();

        URI uri = new URI( authServerUrl );

        try( XXIConnectClient client = new XXIConnectClient(uri) ) {

            final XXIConnectClient.AuthSuccess auth = client.authenticate( dbUrl, login, password.toCharArray());
            final AbstractDataSourceCfgBuilder cfgBuilder;

            if( auth.dbVendor().toLowerCase().contains("postgres"))
                cfgBuilder = new Pg_DataSourceCfgBuilder(appId);
            else
                cfgBuilder = new Ora_DataSourceCfgBuilder(appId);

            return
                cfgBuilder
                    .dbUrl(auth.dbUrl())
                        .login(auth.dbLogin())
                            .password(String.valueOf(auth.dbPassword()))
                    .usePseudoConnection(false)
                .build();
        }
    }
}
