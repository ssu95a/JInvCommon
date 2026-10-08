package ru.inversion.utils.dco;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;


public class DcoJsonTransformTest {

   private static final String IDENTITY_XSLT =
           "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                   "<xsl:stylesheet version=\"1.0\"\n" +
                   "                xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" +
                   "    <xsl:output method=\"xml\" omit-xml-declaration=\"yes\"/>\n" +
                   "    <xsl:template match=\"@*|node()\">\n" +
                   "        <xsl:copy>\n" +
                   "            <xsl:apply-templates select=\"@*|node()\"/>\n" +
                   "        </xsl:copy>\n" +
                   "    </xsl:template>\n" +
                   "</xsl:stylesheet>";

   private static final String CLIENT_TO_PERSON_XSLT =
           "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                   "<xsl:stylesheet version=\"1.0\"\n" +
                   "                xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" +
                   "    <xsl:output method=\"xml\" omit-xml-declaration=\"yes\"/>\n" +
                   "    <xsl:strip-space elements=\"*\"/>\n" +
                   "\n" +
                   "    <xsl:template match=\"client\">\n" +
                   "        <person>\n" +
                   "            <xsl:attribute name=\"code\">\n" +
                   "                <xsl:value-of select=\"id\"/>\n" +
                   "            </xsl:attribute>\n" +
                   "            <fullName>\n" +
                   "                <xsl:value-of select=\"name\"/>\n" +
                   "            </fullName>\n" +
                   "        </person>\n" +
                   "    </xsl:template>\n" +
                   "</xsl:stylesheet>";

   @Test
   public void parseXmlShouldIgnoreFormattingWhitespace() {

      IDco dco = Dco.parseXml(
              "<user>\n" +
                      "    <name>Sergey</name>\n" +
                      "</user>"
      );

      assertEquals("user", dco.getName());
      assertNull(dco.get());
      assertEquals("Sergey", dco.getIfPresent("name"));

      String json = dco.asJson();

      assertFalse(json.contains("\"#value\""));
      assertTrue(json.contains("\"name\""));
      assertTrue(json.contains("\"Sergey\""));
   }

   @Test
   public void jsonShouldSupportExplicitRootObject() {

      IDco dco = Dco.parseJson(
              "request",
              "{\n" +
                      "  \"id\": 100,\n" +
                      "  \"name\": \"Sergey\",\n" +
                      "  \"active\": true\n" +
                      "}"
      );

      assertEquals("request", dco.getName());
      assertEquals(new BigDecimal("100"), dco.getIfPresent("id"));
      assertEquals("Sergey", dco.getIfPresent("name"));
      assertEquals(Boolean.TRUE, dco.getIfPresent("active"));
   }

   @Test
   public void jsonShouldSupportStrictSingleRootObject() {

      IDco dco = Dco.parseJson(
              "{\n" +
                      "  \"user\": {\n" +
                      "    \"@id\": \"100\",\n" +
                      "    \"#value\": \"Sergey\"\n" +
                      "  }\n" +
                      "}"
      );

      assertEquals("user", dco.getName());
      assertEquals("100", dco.getIfPresent("@id"));
      assertEquals("Sergey", dco.get());
   }

   @Test
   public void jsonShouldSupportRepeatedChildrenAsArray() {

      IDco dco = Dco.parseJson(
              "{\n" +
                      "  \"roles\": {\n" +
                      "    \"role\": [\"dev\", \"architect\"]\n" +
                      "  }\n" +
                      "}"
      );

      assertEquals("roles", dco.getName());
      assertEquals(2, dco.count("role"));

      String json = dco.asJson();

      assertTrue(json.contains("\"role\""));
      assertTrue(json.contains("\"dev\""));
      assertTrue(json.contains("\"architect\""));
   }

   @Test
   public void jsonShouldRejectNestedArrays() {

      try {
         Dco.parseJson(
                 "{\n" +
                         "  \"root\": {\n" +
                         "    \"item\": [[1, 2]]\n" +
                         "  }\n" +
                         "}"
         );

         fail("Nested JSON arrays must be rejected");
      }
      catch( DcoException expected ) {
         assertTrue(expected.getMessage().contains("[JSON]"));
      }
   }

   @Test
   public void transformShouldSupportIdentityXslt() {

      IDco source = Dco.parseXml(
              "<root>\n" +
                      "    <item>1</item>\n" +
                      "</root>"
      );

      IDco result = source.transform(
              new StreamSource(new StringReader(IDENTITY_XSLT))
      );

      assertNotSame(source, result);
      assertEquals("root", result.getName());
      assertEquals("1", result.getIfPresent("item"));
   }

   @Test
   public void transformShouldCreateDifferentDcoShape() {

      IDco source = Dco.parseXml(
              "<client>\n" +
                      "    <id>100</id>\n" +
                      "    <name>Sergey</name>\n" +
                      "</client>"
      );

      IDco result = source.transform(
              new StreamSource(new StringReader(CLIENT_TO_PERSON_XSLT))
      );

      assertEquals("person", result.getName());
      assertEquals("100", result.getIfPresent("@code"));
      assertEquals("Sergey", result.getIfPresent("fullName"));
      assertFalse(result.hasItem("id"));
      assertFalse(result.hasItem("name"));
   }

   @Test
   public void withoutNamespacesShouldRemovePrefixedElementAndAttributeNames() {

      IDco source = Dco.parseXml(
              "<ns:user xmlns:ns=\"urn:test\" ns:id=\"100\">\n" +
                      "    <ns:name>Sergey</ns:name>\n" +
                      "</ns:user>"
      );

      IDco result = source.withoutNamespaces();

      assertEquals("user", result.getName());
      assertEquals("100", result.getIfPresent("@id"));
      assertEquals("Sergey", result.getIfPresent("name"));

      assertFalse(result.hasAttribute("xmlns:ns"));
      assertFalse(result.hasAttribute("ns:id"));
      assertFalse(result.hasItem("ns:name"));
   }

   @Test
   public void withoutNamespacesShouldRemoveDefaultNamespaceAttribute() {

      IDco source = Dco.parseXml(
              "<user xmlns=\"urn:test\">\n" +
                      "    <name>Sergey</name>\n" +
                      "</user>"
      );

      IDco result = source.withoutNamespaces();

      assertEquals("user", result.getName());
      assertEquals("Sergey", result.getIfPresent("name"));
      assertFalse(result.hasAttribute("xmlns"));
   }

   @Test
   public void xmlJsonRoundTripShouldKeepAttributesValuesAndChildren() {

      IDco source = Dco.parseXml(
              "<user id=\"100\">\n" +
                      "    <name>Sergey</name>\n" +
                      "    <roles>\n" +
                      "        <role>dev</role>\n" +
                      "        <role>architect</role>\n" +
                      "    </roles>\n" +
                      "</user>"
      );

      String json = source.asJson();

      IDco parsed = Dco.parseJson(json);

      assertEquals("user", parsed.getName());
      assertEquals("100", parsed.getIfPresent("@id"));
      assertEquals("Sergey", parsed.getIfPresent("name"));
      assertEquals(1, parsed.count("roles"));
      assertEquals(2, parsed.single("roles").get().count("role"));
   }

   @Test
   public void stripNamespacesXsltResourceShouldExist() {
      assertNotNull(
              Dco.class.getResource("/ru/inversion/utils/dco/strip-namespaces.xsl")
      );
   }
}