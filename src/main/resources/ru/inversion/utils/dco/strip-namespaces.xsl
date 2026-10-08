<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" omit-xml-declaration="yes"/>
    <xsl:strip-space elements="*"/>

    <xsl:template match="*">
        <xsl:variable name="newName">
            <xsl:choose>
                <xsl:when test="string-length(local-name()) &gt; 0">
                    <xsl:value-of select="local-name()"/>
                </xsl:when>
                <xsl:when test="contains(name(), ':')">
                    <xsl:value-of select="substring-after(name(), ':')"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="name()"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsl:element name="{$newName}">
            <xsl:apply-templates select="@*|node()"/>
        </xsl:element>
    </xsl:template>

    <xsl:template match="@*">
        <xsl:if test="namespace-uri() != 'http://www.w3.org/2000/xmlns/' and not(starts-with(name(), 'xmlns'))">
            <xsl:variable name="newName">
                <xsl:choose>
                    <xsl:when test="string-length(local-name()) &gt; 0">
                        <xsl:value-of select="local-name()"/>
                    </xsl:when>
                    <xsl:when test="contains(name(), ':')">
                        <xsl:value-of select="substring-after(name(), ':')"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="name()"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:variable>

            <xsl:attribute name="{$newName}">
                <xsl:value-of select="."/>
            </xsl:attribute>
        </xsl:if>
    </xsl:template>

    <xsl:template match="text()|comment()|processing-instruction()">
        <xsl:copy/>
    </xsl:template>

</xsl:stylesheet>