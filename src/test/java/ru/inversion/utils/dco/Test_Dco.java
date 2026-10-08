package ru.inversion.utils.dco;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Test_Dco {

    static private final String XML = "<GetRequestResponse>\n" +
            "  <RequestMessage>\n" +
            "    <Request Id=\"SIGNED_BY_SMEV\">\n" +
            "      <SenderProvidedRequestData Id=\"SIGNED_BY_CALLER\">\n" +
            "        <MessageID>9399c3ca-2b03-11f1-a32c-a615a6b6569e</MessageID>\n" +
            "        <MessagePrimaryContent>\n" +
            "          <Request>\n" +
            "            <fileID>terrabyte://00/82317725738/3662b526-cdba-46c7-8398-4c74cf6868df/103</fileID>\n" +
            "            <version>1.0.0</version>\n" +
            "            <type>Передача одним файлом в формате CSV с периодичностью 1 раз в 4 часа</type>\n" +
            "            <dateTime>2026-03-29 02:55:00.000000+03</dateTime>\n" +
            "            <quantity>1241510</quantity>\n" +
            "            <RoutingCode>770K01</RoutingCode>\n" +
            "          </Request>\n" +
            "        </MessagePrimaryContent>\n" +
            "        <RefAttachmentHeaderList>\n" +
            "          <RefAttachmentHeader>\n" +
            "            <uuid>939b988b-2b03-11f1-a32c-a615a6b6569e</uuid>\n" +
            "            <Hash>0xbVv++6kzuy2opwRLYJwY72qlZwvmItkzEz7zsWuKE=</Hash>\n" +
            "            <MimeType>application/zip</MimeType>\n" +
            "            <SignaturePKCS7>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</SignaturePKCS7>\n" +
            "          </RefAttachmentHeader>\n" +
            "        </RefAttachmentHeaderList>\n" +
            "        <BusinessProcessMetadata/>\n" +
            "      </SenderProvidedRequestData>\n" +
            "      <MessageMetadata>\n" +
            "        <MessageId>9399c3ca-2b03-11f1-a32c-a615a6b6569e</MessageId>\n" +
            "        <MessageType>REQUEST</MessageType>\n" +
            "        <Sender>\n" +
            "          <Mnemonic>MNSV03</Mnemonic>\n" +
            "        </Sender>\n" +
            "        <SendingTimestamp>2026-03-29T03:10:24.598+03:00</SendingTimestamp>\n" +
            "        <Recipient>\n" +
            "          <Mnemonic>770K01</Mnemonic>\n" +
            "        </Recipient>\n" +
            "        <DeliveryTimestamp>2026-03-30T17:08:54.319+03:00</DeliveryTimestamp>\n" +
            "        <Status>messageIsDelivered</Status>\n" +
            "      </MessageMetadata>\n" +
            "      <FSAttachmentsList>\n" +
            "        <FSAttachment>\n" +
            "          <uuid>939b988b-2b03-11f1-a32c-a615a6b6569e</uuid>\n" +
            "          <UserName>7QKVbhX6ZfMEDsXq5tl6X6l6V7dyjy</UserName>\n" +
            "          <Password>cUertQn7RQaXYnzQ7HZlfL93qtds09</Password>\n" +
            "          <FileName>/3662b526-cdba-46c7-8398-4c74cf6868df.zip</FileName>\n" +
            "        </FSAttachment>\n" +
            "      </FSAttachmentsList>\n" +
            "      <ReplyTo>eyJzaWQiOjM0NTA3LCJtaWQiOiI5Mzk5YzNjYS0yYjAzLTExZjEtYTMyYy1hNjE1YTZiNjU2OWUiLCJlb2wiOjAsInNsYyI6IngtYXJ0ZWZhY3RzLXJrbF9wZXJzb25zLWxpc3QtdG8ta29fMS4wLjBfUmVxdWVzdCIsIm1ubSI6Ik1OU1YwMyIsIm5zIjoidXJuOi8veC1hcnRlZmFjdHMtcmtsL3BlcnNvbnMtbGlzdC10by1rby8xLjAuMCIsInJlb2wiOjAsIm9yaWQiOm51bGx9</ReplyTo>\n" +
            "    </Request>\n" +
            "  </RequestMessage>\n" +
            "</GetRequestResponse>\n" +
            "\n" +
            " ";

    @BeforeAll
    static void setup() {
    }

    @Test
    @DisplayName("Number conversions - Boolean")
    void test_ParseAndSet() {
        System.out.println(
        Dco.parseXml("<user>\n  <name>Sergey</name>\n</user>").asJson() );


        System.out.println( Dco.parseXml(XML).asJson() );
        //IDco dcoFS = dcoReq.e("FSAttachment");
    }

}
