package ru.inversion.dataset;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedNativeQuery;
import java.io.Serializable;
import java.time.LocalDate;

/**
@author  ssu
@since   2020/03/20 21:04:55
*/
@Entity (name="PCusTest")
@NamedNativeQuery (
    name="",
    query="select * from ( SELECT A.ICUSNUM, A.CCUSLAST_NAME CL_NAME, A.CCUSFIRST_NAME CM_NAME, A.CCUSMIDDLE_NAME CF_NAME,"
	+"       A.DCUSBIRTHDAY DBTH,"
	+"       A.CCUSSNILS CSNILS,"
	+"       B.DOC_SER CDOC_SER,"
	+"       B.DOC_NUM CDOC_NUM,"
	+"       B.DOC_DATE DDOC_ISSUE_DATE,"
	+"       B.DOC_SUBDIV CDOC_ISSUE_CODE,"
	+"       ~(SELECT P.PH_CITY || P.PH_NUM PH_NUM"
	+"          FROM CUS_PHONE P"
	+"         WHERE P.ICUSNUM = A.ICUSNUM"
	+"           AND ROWNUM = 1) CPHONE"
	+"  FROM VXCUS A, CUS_DOCUM B"
	+" WHERE A.ICUSNUM = B.ICUSNUM"
	+"   AND B.ID_DOC_TP = 21"
	+"   AND A.CCUSFLAG IN ('1', '4')"
	+"   AND A.CCUSSNILS is not null ~ ) x")
public class PCusTest implements Serializable
{
    private static final long serialVersionUID = 20_03_2020_21_04_55l;

    private Long ICUSNUM;
    private String CL_NAME;
    private String CM_NAME;
    private String CF_NAME;
    private LocalDate DBTH;
    private String CSNILS;
    private String CDOC_SER;
    private String CDOC_NUM;
    private LocalDate DDOC_ISSUE_DATE;
    private String CDOC_ISSUE_CODE;
    private String CPHONE;

    public PCusTest(){}

    @Id 
    @Column(name="ICUSNUM",nullable = false)
    public Long getICUSNUM() {
        return ICUSNUM;
    }
    public void setICUSNUM(Long val) {
        ICUSNUM = val; 
    }
    @Column(name="CL_NAME",length = 20)
    public String getCL_NAME() {
        return CL_NAME;
    }
    public void setCL_NAME(String val) {
        CL_NAME = val; 
    }
    @Column(name="CM_NAME",length = 20)
    public String getCM_NAME() {
        return CM_NAME;
    }
    public void setCM_NAME(String val) {
        CM_NAME = val; 
    }
    @Column(name="CF_NAME",length = 20)
    public String getCF_NAME() {
        return CF_NAME;
    }
    public void setCF_NAME(String val) {
        CF_NAME = val; 
    }
    @Column(name="DBTH")
    public LocalDate getDBTH() {
        return DBTH;
    }
    public void setDBTH(LocalDate val) {
        DBTH = val; 
    }
    @Column(name="CSNILS",length = 15)
    public String getCSNILS() {
        return CSNILS;
    }
    public void setCSNILS(String val) {
        CSNILS = val; 
    }
    @Column(name="CDOC_SER",length = 5)
    public String getCDOC_SER() {
        return CDOC_SER;
    }
    public void setCDOC_SER(String val) {
        CDOC_SER = val; 
    }
    @Column(name="CDOC_NUM",length = 6)
    public String getCDOC_NUM() {
        return CDOC_NUM;
    }
    public void setCDOC_NUM(String val) {
        CDOC_NUM = val; 
    }
    @Column(name="DDOC_ISSUE_DATE")
    public LocalDate getDDOC_ISSUE_DATE() {
        return DDOC_ISSUE_DATE;
    }
    public void setDDOC_ISSUE_DATE(LocalDate val) {
        DDOC_ISSUE_DATE = val; 
    }
    @Column(name="CDOC_ISSUE_CODE",length = 7)
    public String getCDOC_ISSUE_CODE() {
        return CDOC_ISSUE_CODE;
    }
    public void setCDOC_ISSUE_CODE(String val) {
        CDOC_ISSUE_CODE = val; 
    }
    @Column(name="CPHONE",length = 10)
    public String getCPHONE() {
        return CPHONE;
    }
    public void setCPHONE(String val) {
        CPHONE = val; 
    }
}