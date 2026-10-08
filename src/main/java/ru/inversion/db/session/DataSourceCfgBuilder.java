package ru.inversion.db.session;

import ru.inversion.db.session.cfg_impl.Ora_DataSourceCfgBuilder;

public class DataSourceCfgBuilder extends Ora_DataSourceCfgBuilder {
    /**
     * @param appId
     */
    public DataSourceCfgBuilder( String appId ) {
        super(appId);
    }
}
