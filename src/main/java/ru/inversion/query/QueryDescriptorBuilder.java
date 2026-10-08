package ru.inversion.query;

import ru.inversion.dataset.mark.MarkDescriptor;
import ru.inversion.query.node.INode;
import ru.inversion.query.node.MarkNode;
import ru.inversion.utils.S;

import java.util.Optional;

import static ru.inversion.dataset.mark.MarkModeEnum.*;

/** */
public class QueryDescriptorBuilder {

    /** */
    private String query;

    /** */
    private String orderBy;

    /** */
    private String where;

    /** */
    private Class entityClass;

    /** */
    private MarkDescriptor markDescriptor;

    /** */
    private String alias;

    /** */
    private String optimizerHint;

    /** */
    public QueryDescriptorBuilder query  ( String query ) {
        this.query = query;
        return this;
    }

    public QueryDescriptorBuilder orderBy( String orderBy ) {
        this.orderBy = orderBy;
        return this;
    }

    public QueryDescriptorBuilder where  ( String where ) {
        this.where = where;
        return this;
    }

    public QueryDescriptorBuilder markMode( MarkDescriptor markDescriptor ) {
        this.markDescriptor = markDescriptor;
        return this;
    }

    public QueryDescriptorBuilder queryAlias( String alias ) {
        this.alias = alias;
        return this;
    }

    public QueryDescriptorBuilder optimizerHint( String optimizerHint ) {
        this.optimizerHint = optimizerHint;
        return this;
    }

    /** */
    private Optional< INode > createMarkNode( String selectFrom ) {

        if( markDescriptor == null || markDescriptor.getMarkMode() == NONE )
            return Optional.empty();

        final String pkColumns[] = markDescriptor.getMarkKeyColumns( );

        if( pkColumns == null || pkColumns.length == 0 )
            return Optional.empty();


        if( S.isNullOrEmpty(selectFrom) )
            selectFrom = S.EMPTY_STRING;
        else
            selectFrom += '.';

        StringBuilder sb = new StringBuilder();

        if( markDescriptor.getMarkMode() == MRK )
        {
            sb.append( "nvl( (select 1 from dual where exists ( select null from v_jf_mrk a$ where a$.IMRKMARKERID = :jinv_marker_id" );
            sb.append( " and a$.RMRKROWID = ").append( selectFrom )
              .append( pkColumns[0] )
              .append( ")),0) " );
        }
        else
        {
            if ( pkColumns.length == 1 )
            {
                if( markDescriptor.getMarkMode() == MRK_ID )
                {
                    sb.append( "nvl((select 1 from dual where exists ( select null from v_jf_mrk_id a$ where a$.idmarker = :jinv_marker_id" )
                      .append( " and a$.idrow = " )
                      .append( selectFrom )
                      .append( pkColumns[0] )
                      .append( ")),0) " );
                }
                else
                    //if( markDescriptor.getMarkMode() == MRK_U )
                    {
                        sb.append( "nvl((select 1 from dual where exists ( select null from v_jf_mrk_u a$ where a$.idmarker = :jinv_marker_id" )
                          .append( " and a$.UROW = " )
                          .append( selectFrom )
                          .append( pkColumns[0] )
                          .append( ")),0) ");
                    }
            }
            else
            {
                  sb.append( "nvl((select 1 from dual where exists ( select null from v_jf_mrk_id2 a$ where idmarker = :jinv_marker_id" )
                    .append( " and a$.IDROW  = ")
                    .append( selectFrom )
                    .append( pkColumns[0] )
                    .append( " and a$.IDAROW = ")
                    .append( selectFrom )
                    .append( pkColumns[1] )
                    .append( ")),0) " );
            }
        }

        return Optional.of( new MarkNode( ) );
    }

    /** */
    private String getSqlQuery() {
        return this.query;
    }

    /** */
    public QueryDescriptor build( ) {

        final String sqlQuery = getSqlQuery();



        return null;
    }

    /** */
    public static QueryDescriptorBuilder of( Class entityClass ) {
        return null;
    }
}
