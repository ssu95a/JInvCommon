package ru.inversion.dataset.mark;

import ru.inversion.dataset.XXIDataSet;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import javax.persistence.Column;
import javax.persistence.Id;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static ru.inversion.dataset.mark.MarkModeEnum.MRK_CUSTOM;
import static ru.inversion.dataset.mark.MarkModeEnum.NONE;

/**
 * Настроечные данные пометки для XXIDataSet.
 * <p>
 * Строится на основе аннотаций класса pojo.
 * Сам по себе не используется.
 * 
 * @see XXIDataSet
 * 
 * @author sulimoff
 */
public class MarkDescriptor {

    final static public  MarkDescriptor g_emptyInstance  = new MarkDescriptor(null);
    final static private MarkDescriptor g_customInstance = new CustomMarkDescriptor();
    final static private String[]       g_errKeyColumns  = new String[]{"JINV_ERR_NO_MARK_COL_DEF1","JINV_ERR_NO_MARK_COL_DEF2"};
            
    private MarkModeEnum markMode       = NONE;
    private String[]     markKeyColumns = null;
    private String       markTable      = null;

    // TODO:
    // Добавить режим - временной таблицы
    public MarkDescriptor( Class clazz ) {
        
        markMode = MarkModeEnum.fromClass( clazz );

        if( markMode == NONE )
            return;

        if( clazz.isAnnotationPresent(MarkColumns.class) ) {

            MarkColumns mc = (MarkColumns)clazz.getAnnotation(MarkColumns.class);
            
            markKeyColumns = mc.columns();
        }//end if
        
        if( markKeyColumns == null || markKeyColumns.length == 0 ) {
            
            Method[] methods = clazz.getMethods();

            List<String> mc = new ArrayList<>();

            for( Method m : methods ) {
                
                if( m.isAnnotationPresent(Id.class) && m.isAnnotationPresent(Column.class) ) {
                
                    Column c = m.getAnnotation(Column.class);
                    
                    if( S.isNullOrEmpty( c.name() ) )
                        continue;

                    mc.add( c.name() );
                }
            }//end for

            if( !mc.isEmpty() && mc.size() < 3 )
            {
                markKeyColumns = new String[mc.size()];
                markKeyColumns = mc.toArray(markKeyColumns);
            }
        }
        
        if( markKeyColumns == null )
            markMode = NONE;

        if( clazz.isAnnotationPresent( MarkTable.class ) ) {

            MarkTable mt = (MarkTable)clazz.getAnnotation(MarkTable.class);

            markTable = mt.name();
        }

    }
    
    /** */
    public MarkModeEnum getMarkMode( ) {
        return markMode;
    }
    
    /** */
    public String[] getMarkKeyColumns() {
        return U.nvl( markKeyColumns, g_errKeyColumns ); 
    }

    /** */
    public String getMarkTable() { return markTable; }

    public static class CustomMarkDescriptor extends MarkDescriptor {
        private final MarkDescriptor base;
        private CustomMarkDescriptor( ) {
            super(null);
            base = g_emptyInstance;
        }
        private CustomMarkDescriptor( MarkDescriptor mk ) {
            super(null);
            base = mk;
        }
        public MarkModeEnum getMarkMode( ) {
            return MRK_CUSTOM;
        }
        /** */
        public String[] getMarkKeyColumns() {
            return base.getMarkKeyColumns();
        }
        /** */
        public String getMarkTable() { return base.getMarkTable(); }

        /** */
        public MarkDescriptor getBase() {return base;}
    }

    /** */
    public MarkDescriptor toCustom( )
    {
        return this.markMode == NONE ? g_customInstance : new CustomMarkDescriptor(this);
    }
}
