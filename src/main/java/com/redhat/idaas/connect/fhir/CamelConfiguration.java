/*
 * Copyright 2019 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */
package com.redhat.idaas.connect.fhir;

import ca.uhn.fhir.store.IAuditDataStore;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hl7.HL7;
import org.apache.camel.component.hl7.HL7MLLPNettyDecoderFactory;
import org.apache.camel.component.hl7.HL7MLLPNettyEncoderFactory;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
//import org.springframework.jms.connection.JmsTransactionManager;
//import javax.jms.ConnectionFactory;
import org.springframework.stereotype.Component;
import sun.util.calendar.BaseCalendar;
import java.time.LocalDate;

@Component
public class CamelConfiguration extends RouteBuilder {
  private static final Logger log = LoggerFactory.getLogger(CamelConfiguration.class);

  @Bean
  private KafkaEndpoint kafkaEndpoint(){
    KafkaEndpoint kafkaEndpoint = new KafkaEndpoint();
    return kafkaEndpoint;
  }
  @Bean
  private KafkaComponent kafkaComponent(KafkaEndpoint kafkaEndpoint){
    KafkaComponent kafka = new KafkaComponent();
    return kafka;
  }

  /*
   * Kafka implementation based upon https://camel.apache.org/components/latest/kafka-component.html
   *
   */
  @Override
  public void configure() throws Exception {

    /*
     * Audit
     *
     * Direct component within platform to ensure we can centralize logic
     * There are some values we will need to set within every route
     * We are doing this to ensure we dont need to build a series of beans
     * and we keep the processing as lightweight as possible
     *
     */
    from("direct:auditing")
        .setHeader("messageprocesseddate").simple("${date:now:yyyy-MM-dd}")
        .setHeader("messageprocessedtime").simple("${date:now:HH:mm:ss:SSS}")
        .setHeader("processingtype").exchangeProperty("processingtype")
        .setHeader("industrystd").exchangeProperty("industrystd")
        .setHeader("component").exchangeProperty("componentname")
        .setHeader("messagetrigger").exchangeProperty("messagetrigger")
        .setHeader("processname").exchangeProperty("processname")
        .setHeader("auditdetails").exchangeProperty("auditdetails")
        .setHeader("camelID").exchangeProperty("camelID")
        .setHeader("exchangeID").exchangeProperty("exchangeID")
        .setHeader("internalMsgID").exchangeProperty("internalMsgID")
        .setHeader("bodyData").exchangeProperty("bodyData")
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=opsMgmt_PlatformTransactions&brokers=localhost:9092")
    ;
    /*
    *  Logging
    */
    from("direct:logging")
        .log(LoggingLevel.INFO, log, "HL7 Admissions Message: [${body}]")
        //To invoke Logging
        //.to("direct:logging")
    ;

    /*
     *  Clinical FHIR
     *  ----
     * these will be accessible within the integration when started the default is
     * <hostname>:8080/idaas/<resource>
     * FHIR Resources:
     *  CodeSystem,DiagnosticResult,Encounter,EpisodeOfCare,Immunization,MedicationRequest
     *  MedicationAdminstration,Observation,Order,Patient,Procedure,Schedule
     */

    from("servlet://adverseevent")
        .routeId("FHIRAdverseEvent")
        // set Auditing Properties
        .convertBodyTo(String.class)
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("AdverseEvent")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Adverse Event message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_AdverseEvent&brokers=localhost:9092")
        // Invoke External FHIR Server
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/adverseevents?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("adverseevents")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("adverseevents FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
      ;
    from("servlet://alergyintollerance")
        .routeId("FHIRAllergyIntollerance")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("messagetrigger").constant("AllergyIntollerance")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Allergy Intollerance message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_AllergyIntellorance&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/allergyintollerance?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("allergyintollerance")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("allergyintollerance FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")// Invoke External FHIR Server
    ;
    from("servlet://appointment")
         .routeId("FHIRAppointment")
         .convertBodyTo(String.class)
         // set Auditing Properties
         .setProperty("processingtype").constant("data")
         .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
         .setProperty("industrystd").constant("FHIR")
         .setProperty("messagetrigger").constant("Appointment")
         .setProperty("component").simple("${routeId}")
         .setProperty("camelID").simple("${camelId}")
         .setProperty("exchangeID").simple("${exchangeId}")
         .setProperty("internalMsgID").simple("${id}")
         .setProperty("bodyData").simple("${body}")
         .setProperty("processname").constant("Input")
         .setProperty("auditdetails").constant("Appointment message received")
         // iDAAS DataHub Processing
         .wireTap("direct:auditing")
         // Send To Topic
         .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Appointment&brokers=localhost:9092")
         //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
         //.to("jetty:http://localhost:8090/fhir-server/api/v4/appointment?bridgeEndpoint=true&exchangePattern=InOut")
         //Process Response
         //.convertBodyTo(String.class)
         // set Auditing Properties
         //.setProperty("processingtype").constant("data")
         //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
         //.setProperty("industrystd").constant("FHIR")
         //.setProperty("messagetrigger").constant("appointment")
         //.setProperty("component").simple("${routeId}")
         //.setProperty("processname").constant("Response")
         //.setProperty("camelID").simple("${camelId}")
         //.setProperty("exchangeID").simple("${exchangeId}")
         //.setProperty("internalMsgID").simple("${id}")
         //.setProperty("bodyData").simple("${body}")
         //.setProperty("auditdetails").constant("appointment FHIR response message received")
         // iDAAS DataHub Processing
         //.wireTap("direct:auditing")// Invoke External FHIR Server
    ;
    from("servlet://appointmentresponse")
        .routeId("FHIRAppointmentResponse")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("AppointmentResponse")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Appointment Response message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_AppointmentResponse&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/appointmentresponse?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("appointmentresponse")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("appointmentresponse FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")// Invoke External FHIR Server
    ;
    from("servlet://careplan")
        .routeId("FHIRCarePlan")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("CarePlan")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("CarePlan message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_CarePlan&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/careplan?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("careplan")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("careplan FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")// Invoke External FHIR Server
    ;
    from("servlet://careteam")
        .routeId("FHIRCareTeam")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("CareTeam")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("CareTeam message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_CareTeam&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/careteam?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("careteam")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("careteam FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")// Invoke External FHIR Server
        // Send To Topic
        //.wireTap("direct:auditing")
    ;
    from("servlet://codesystem")
        .routeId("FHIRCodeSystem")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("CodeSystem")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("CodeSystem message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_CodeSystem&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/codesystem?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("codesystem")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("codesystem FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://consent")
        .routeId("FHIRConsent")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Consent")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Consent message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Consent&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/consent?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("consent")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("consent FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://clincialimpression")
        .routeId("FHIRCodeSystem")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("ClinicalImpression")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("ClinicalImpression message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_ClinicalImpression&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/clinicalimpression?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("clinicalimpression")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("clinicalimpression FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://communication")
        .routeId("FHIRCommunication")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Communication")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Communication message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Communication&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/communication?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("communication")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("communication FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://condition")
        .routeId("FHIRCondition")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Condition")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Condition message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Condition&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/communication?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("condition")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("condition FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://detectedissue")
        .routeId("FHIRDetectedIssue")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("DetectedIssue")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Detected Issue message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_DetectedIssue&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/detectedissue?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("detectedissue")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("detectedissue FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://device")
        .routeId("FHIRDevice")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Device")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Device message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Device&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/device?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("device")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("device FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://devicerequest")
        .routeId("FHIRDeviceRequest")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("DeviceRequest")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Device Request message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_DeviceRequest&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/devicerequest?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("devicerequest")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("devicerequest FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://deviceusestatement")
        .routeId("FHIRDeviceUseStatement")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("DeviceUseStatement")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Device Use Statement message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_DeviceUseStatement&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/deviceusestatement?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("deviceusestatement")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("deviceusestatement FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://diagnosticresult")
        .routeId("FHIRDiagnosticResult")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("DiagnosticResult")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("DiagnosticResult message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_DeviceResult&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/deviceresult?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("deviceresult")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("deviceresult FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://effectevidencesynthesis")
        .routeId("FHIREffectEvidenceSynthesis")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("EffectEvidenceSynthesis")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Effect Evidence Synthesis message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_EffectEvidenceSynthesis&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/effectevidencesynthesis?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("effectevidencesynthesis")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("effectevidencesynthesis FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://encounter")
        .routeId("FHIREncounter")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Encounter")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Encounter message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Encounter&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/encounter?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("encounter")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("encounter FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://episodeofcare")
        .routeId("FHIREpisodeOfCare")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("EpisodeOfCare")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("EpisodeOfCare message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_EpisodeOfCare&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/episodeofcare?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("episodeofcare")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("episodeofcare FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://evidence")
        .routeId("FHIREvidence")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Evidence")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Evidence message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Evidence&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/evidence?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("evidence")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("evidence FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://evidencevariable")
        .routeId("FHIREvidenceVariable")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("EvidenceVariable")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Evidence Variable message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_EvidenceVariable&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/evidencevariable?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("evidencevariable")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("evidencevariable FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://goal")
        .routeId("FHIRGoal")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Goal")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Goal message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Goal&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/goal?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("goal")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("goal FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://healthcareservice")
        .routeId("FHIRHealthcareService")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("HealthcareService")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("HealthcareService message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_HealthcareService&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/healthcareservice?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("healthcareservice")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("healthcareservice FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://imagingstudy")
        .routeId("FHIRImagingStudy")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("ImagingStudy")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Imaging Study message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_ImagingStudy&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/imagingstudy?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("imagingstudy")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("imagingstudy FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://location")
        .routeId("FHIRLocation")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Location")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Location message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Location&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/location?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("location")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("location FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://measure")
        .routeId("FHIRMeasure")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Measure")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Measure message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Measure&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/measure?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("measure")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("measure FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://measurereport")
        .routeId("FHIRMeasureReport")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("MeasureReport")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Measure Report message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_MeasureReport&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/measurereport?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("measurereport")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("measurereport FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://medicationrequest")
        .routeId("FHIRMedicationRequest")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("MedicationRequest")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Medication Request message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_MedicationRequest&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/medicationrequest?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("medicationrequest")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("meedicationrequest FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://medicationadministration")
        .routeId("FHIRMedicationAdministration")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("MedicationAdministration")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Medication Statement message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_MedicationAdministration&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/medicationadministration?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("medicationadministration")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("medicationadministration FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://observation")
        .routeId("FHIRObservation")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Observation")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Observation message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Observation&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/observation?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("observation")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("observation FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://order")
        .routeId("FHIROrder")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Order")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Order message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Order&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/order?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("order")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("order FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://organization")
        .routeId("FHIROrganization")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Organization")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Organization message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Organization&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/organization?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("organization")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("organization FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://organizationaffiliation")
        .routeId("FHIROrganizationAffiliation")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("OrganizationAffiliation")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Organization Affiliation message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_OrganizationAffiliation&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/organizationaffiliation?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("organizationaffiliation")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("organizationaffiliation FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://patient")
        .routeId("FHIRPatient")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Patient")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Patient message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Patient&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/patient?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("patient")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("patient FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://person")
        .routeId("FHIRPerson")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Person")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Person message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Person&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/person?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("person")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("person FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://practitioner")
        .routeId("FHIRPractitioner")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Practitioner")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("auditdetails").constant("Practitioner message received")
        .setProperty("bodyData").simple("${body}")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Practitioner&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/practitioner?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("practitioner")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("practitioner FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://procedure")
        .routeId("FHIRProcedure")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Procedure")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Procedure message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Procedure&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/procedure?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("procedure")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("procedure FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://questionaire")
        .routeId("FHIRQuestionaire")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Questionaire")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Questionaire message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvrQuestionaire&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/questionaire?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("questionaire")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("questionaire FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://questionaireresponse")
        .routeId("FHIRQuestionaireResponse")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("QuestionaireResponse")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Questionaire Response message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvrQuestionaireResponse&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/questionaireresponse?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("questionaireresponse")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("questionaireresponse FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://researchelementdefinition")
        .routeId("FHIRResearchElementhDefinition")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("ResearchElementDefinition")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Research Element Definition message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIR_ResearchElementhDefinition&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/researchelementhdefinition?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("researchelementhdefinition")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("researchelementhdefinition FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://researchdefinition")
        .routeId("FHIRResearchDefinition")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("ResearchDefinition")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Research Definition message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIR_ResearchDefinition&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/researchdefinition?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("researchdefinition")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("researchdefinition FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://researchstudy")
        .routeId("FHIRResearchStudy")
        .convertBodyTo(String.class)
         // set Auditing Properties
         .setProperty("processingtype").constant("data")
         .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
         .setProperty("industrystd").constant("FHIR")
         .setProperty("messagetrigger").constant("ResearchStudy")
         .setProperty("component").simple("${routeId}")
         .setProperty("camelID").simple("${camelId}")
         .setProperty("exchangeID").simple("${exchangeId}")
         .setProperty("processname").constant("Input")
         .setProperty("internalMsgID").simple("${id}")
         .setProperty("bodyData").simple("${body}")
         .setProperty("processname").constant("Input")
         .setProperty("auditdetails").constant("Research Study message received")
         // iDAAS DataHub Processing
         .wireTap("direct:auditing")
         // Send To Topic
         .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIR_ResearchStudy&brokers=localhost:9092")
         //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
         //.to("jetty:http://localhost:8090/fhir-server/api/v4/researchstudy?bridgeEndpoint=true&exchangePattern=InOut")
         //Process Response
         //.convertBodyTo(String.class)
         // set Auditing Properties
         //.setProperty("processingtype").constant("data")
         //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
         //.setProperty("industrystd").constant("FHIR")
         //.setProperty("messagetrigger").constant("researchstudy")
         //.setProperty("component").simple("${routeId}")
         //.setProperty("processname").constant("Response")
         //.setProperty("camelID").simple("${camelId}")
         //.setProperty("exchangeID").simple("${exchangeId}")
         //.setProperty("internalMsgID").simple("${id}")
         //.setProperty("bodyData").simple("${body}")
         //.setProperty("auditdetails").constant("researchstudy FHIR response message received")
         // iDAAS DataHub Processing
         //.wireTap("direct:auditing")
    ;
    from("servlet://researchsubject")
        .routeId("FHIRResearchSubject")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("ResearchSubject")
        .setProperty("component").simple("${routeId}")
        .setProperty("camelID").simple("${camelId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Research Subject message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIR_ResearchSubject&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/researchsubject?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("researchsubject")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("researchsubject FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://schedule")
        .routeId("FHIRSchedule")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Schedule")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Schedule message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIR_Schedule&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/schedule?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("schedule")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("schedule FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://servicerequest")
        .routeId("FHIRServiceRequest")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("ServiceRequest")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Service Request message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIR_ServiceRequest&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/servicerequest?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("servicerequest")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("servicerequest FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://specimen")
        .routeId("FHIRSpecimen")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Specimen")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Specimen message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIR_Specimen&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/specimen?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("specimen")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("specimen FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://substance")
        .routeId("FHIRSubstance")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("Substance")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Substance message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIR_Sustance&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/substance?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("substance")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("substance FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://supplydelivery")
        .routeId("FHIRSupplyDelivery")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("SupplyDelivery")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Supply Delivery message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIR_SupplyDelivery&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/supplydelivery?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("supplydelivery")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("supplydelivery FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://supplyrequest")
        .routeId("FHIRSupplyRequest")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("SupplyRequest")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Supply Request message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIR_SupplyRequest&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/supplydelivery?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("supplyrequest")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("supplyrequest FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://testreport")
        .routeId("FHIRTestReport")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("TestReport")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Test Report message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIR_TestReport&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/testreport?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("testreport")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("testreport FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://testscript")
        .routeId("FHIRTestScript")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("TestScript")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Test Script message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIR_TestScript&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/testscript?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("testscript")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("testscript FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;
    from("servlet://verificationresult")
        .routeId("FHIRVerificationResult")
        .convertBodyTo(String.class)
        // set Auditing Properties
        .setProperty("processingtype").constant("data")
        .setProperty("appname").constant("iDAAS-ConnectClinical-IndustryStd")
        .setProperty("industrystd").constant("FHIR")
        .setProperty("messagetrigger").constant("VerificationResult")
        .setProperty("component").simple("${routeId}")
        .setProperty("exchangeID").simple("${exchangeId}")
        .setProperty("processname").constant("Input")
        .setProperty("internalMsgID").simple("${id}")
        .setProperty("bodyData").simple("${body}")
        .setProperty("processname").constant("Input")
        .setProperty("auditdetails").constant("Verification Result message received")
        // iDAAS DataHub Processing
        .wireTap("direct:auditing")
        // Send To Topic
        .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIR_VerificationResult&brokers=localhost:9092")
        //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
        //.to("jetty:http://localhost:8090/fhir-server/api/v4/verificationresult?bridgeEndpoint=true&exchangePattern=InOut")
        //Process Response
        //.convertBodyTo(String.class)
        // set Auditing Properties
        //.setProperty("processingtype").constant("data")
        //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
        //.setProperty("industrystd").constant("FHIR")
        //.setProperty("messagetrigger").constant("verificationresult")
        //.setProperty("component").simple("${routeId}")
        //.setProperty("processname").constant("Response")
        //.setProperty("camelID").simple("${camelId}")
        //.setProperty("exchangeID").simple("${exchangeId}")
        //.setProperty("internalMsgID").simple("${id}")
        //.setProperty("bodyData").simple("${body}")
        //.setProperty("auditdetails").constant("verificationresult FHIR response message received")
        // iDAAS DataHub Processing
        //.wireTap("direct:auditing")
    ;

    /*
     *  Financial FHIR
     */

    from("servlet://http://localhost:8888/fhiraccount")
            .routeId("FHIRAccount")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("account")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("account message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Account&brokers=localhost:9092")
    // Invoke External FHIR Server
    //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
    //.to("https://localhost:9443/fhir-server/api/v4/account")
    // Process Response
    //.convertBodyTo(String.class)
    // set Auditing Properties
    //.setProperty("processingtype").constant("data")
    //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
    //.setProperty("industrystd").constant("FHIR")
    //.setProperty("messagetrigger").constant("account")
    //.setProperty("component").simple("${routeId}")
    //.setProperty("processname").constant("Input")
    //.setProperty("camelID").simple("${camelId}")
    //.setProperty("exchangeID").simple("${exchangeId}")
    //.setProperty("internalMsgID").simple("${id}")
    //.setProperty("bodyData").simple("${body}")
    //.setProperty("auditdetails").constant("account FHIR response message received")
    // iDAAS DataHub Processing
    //.wireTap("direct:auditing")
    ;

    from("servlet://http://localhost:8888/fhirchargeitem")
            .routeId("FHIRChargeItem")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("chargeitem")
            .setProperty("component").simple("${routeId}")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("processname").constant("Input")
            .setProperty("auditdetails").constant("charge item message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_ChargeItem&brokers=localhost:9092")
    // Invoke External FHIR Server
    //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
    //.to("https://localhost:9443/fhir-server/api/v4/chargeitem")
    // Process Response
    //.convertBodyTo(String.class)
    // set Auditing Properties
    //.setProperty("processingtype").constant("data")
    //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
    //.setProperty("industrystd").constant("FHIR")
    //.setProperty("messagetrigger").constant("chargeitem")
    //.setProperty("component").simple("${routeId}")
    //.setProperty("processname").constant("Input")
    //.setProperty("camelID").simple("${camelId}")
    //.setProperty("exchangeID").simple("${exchangeId}")
    //.setProperty("internalMsgID").simple("${id}")
    //.setProperty("bodyData").simple("${body}")
    //.setProperty("auditdetails").constant("chargeitem FHIR response message received")
    // iDAAS DataHub Processing
    //.wireTap("direct:auditing")
    ;

    from("servlet://http://localhost:8888/fhirchargeitemdefinition")
            .routeId("FHIRChargeItemDefintion")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("chargeitemdefinition")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("charge item definition message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_ChargeItemDefinintion&brokers=localhost:9092")
    // Invoke External FHIR Server
    //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
    //.to("https://localhost:9443/fhir-server/api/v4/chargeitemdefinition")
    // Process Response
    //.convertBodyTo(String.class)
    // set Auditing Properties
    //.setProperty("processingtype").constant("data")
    //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
    //.setProperty("industrystd").constant("FHIR")
    //.setProperty("messagetrigger").constant("chargeitemdefintion")
    //.setProperty("component").simple("${routeId}")
    //.setProperty("processname").constant("Input")
    //.setProperty("camelID").simple("${camelId}")
    //.setProperty("exchangeID").simple("${exchangeId}")
    //.setProperty("internalMsgID").simple("${id}")
    //.setProperty("bodyData").simple("${body}")
    //.setProperty("auditdetails").constant("chargeitemdefintion FHIR response message received")
    // iDAAS DataHub Processing
    //.wireTap("direct:auditing")
    ;

    from("servlet://http://localhost:8888/fhircontract")
            .routeId("FHIRContract")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("contract")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("contract message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Contract&brokers=localhost:9092")
    // Invoke External FHIR Server
    //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
    //.to("jetty:http://localhost:8090/fhir-server/api/v4/contract?bridgeEndpoint=true&exchangePattern=InOut")
    // Process Response
    //.convertBodyTo(String.class)
    // set Auditing Properties
    //.setProperty("processingtype").constant("data")
    //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
    //.setProperty("industrystd").constant("FHIR")
    //.setProperty("messagetrigger").constant("contract")
    //.setProperty("component").simple("${routeId}")
    //.setProperty("processname").constant("Response")
    //.setProperty("camelID").simple("${camelId}")
    //.setProperty("exchangeID").simple("${exchangeId}")
    //.setProperty("internalMsgID").simple("${id}")
    //.setProperty("bodyData").simple("${body}")
    //.setProperty("auditdetails").constant("contract FHIR message response received")
    // iDAAS DataHub Processing
    //.wireTap("direct:auditing")
    //Process Response
    ;
    from("servlet://fhirrcoverage")
            .routeId("FHIRCoverage")
            .convertBodyTo(String.class)
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("coverage")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("coverage message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Coverage&brokers=localhost:9092")
    // Invoke External FHIR Server
    //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
    //.to("jetty:http://localhost:8090/fhir-server/api/v4/coverage?bridgeEndpoint=true&exchangePattern=InOut")
    // Process Response
    //.convertBodyTo(String.class)
    // set Auditing Properties
    //.setProperty("processingtype").constant("data")
    //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
    //.setProperty("industrystd").constant("FHIR")
    //.setProperty("messagetrigger").constant("coverage")
    //.setProperty("component").simple("${routeId}")
    //.setProperty("processname").constant("Response")
    //.setProperty("camelID").simple("${camelId}")
    //.setProperty("exchangeID").simple("${exchangeId}")
    //.setProperty("internalMsgID").simple("${id}")
    //.setProperty("bodyData").simple("${body}")
    //.setProperty("auditdetails").constant("coverage FHIR message response received")
    // iDAAS DataHub Processing
    //.wireTap("direct:auditing")
    ;

    from("servlet://http://localhost:8888/fhircoverageeligibilityrequest")
            .routeId("FHIRCoverageEligibilityRequest")
            // set Auditing Properties
            .convertBodyTo(String.class)
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("coverageeligibilityrequest")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("coverageeligibilityrequest message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_CoverageEligibilityRequest&brokers=localhost:9092")
    // Invoke External FHIR Server
    //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
    //.to("jetty:http://localhost:8090/fhir-server/api/v4/coverageeligibilityrequest?bridgeEndpoint=true&exchangePattern=InOut")
    //Process Response
    //.convertBodyTo(String.class)
    // set Auditing Properties
    //.setProperty("processingtype").constant("data")
    //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
    //.setProperty("industrystd").constant("FHIR")
    //.setProperty("messagetrigger").constant("coverageeligibilityrequest")
    //.setProperty("component").simple("${routeId}")
    //.setProperty("processname").constant("Response")
    //.setProperty("camelID").simple("${camelId}")
    //.setProperty("exchangeID").simple("${exchangeId}")
    //.setProperty("internalMsgID").simple("${id}")
    //.setProperty("bodyData").simple("${body}")
    //.setProperty("auditdetails").constant("coverage eligibility request FHIR Response message received")
    // iDAAS DataHub Processing
    //.wireTap("direct:auditing")
    ;

    from("servlet://http://localhost:8888/fhircoverageeligibilityresponse")
            .routeId("FHIRCoverageeligibilityresponse")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("coverageeligibilityresponse")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("coverageeligibilityresponse message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_CoverageEligibilityResponse&brokers=localhost:9092")
    // Invoke External FHIR Server
    //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
    //.to("jetty:http://localhost:8090/fhir-server/api/v4/coverageeligibilityresponse?bridgeEndpoint=true&exchangePattern=InOut")
    //Process Response
    //.convertBodyTo(String.class)
    // set Auditing Properties
    //.setProperty("processingtype").constant("data")
    //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
    //.setProperty("industrystd").constant("FHIR")
    //.setProperty("messagetrigger").constant("coverageeligibilityresponse")
    //.setProperty("component").simple("${routeId}")
    //.setProperty("processname").constant("Response")
    //.setProperty("camelID").simple("${camelId}")
    //.setProperty("exchangeID").simple("${exchangeId}")
    //.setProperty("internalMsgID").simple("${id}")
    //.setProperty("bodyData").simple("${body}")
    //.setProperty("auditdetails").constant("coverage eligibility response FHIR Server response received")
    // iDAAS DataHub Processing
    //.wireTap("direct:auditing")
    ;

    from("servlet://http://localhost:8888/fhirenrollmentrequest")
            .routeId("FHIREnrollmentrequest")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("enrollmentrequest")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("Enrollment Request message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_EnrollmentRequest&brokers=localhost:9092")
    // Invoke External FHIR Server
    //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
    //.to("jetty:http://localhost:8090/fhir-server/api/v4/enrollmentrequest?bridgeEndpoint=true&exchangePattern=InOut")
    // Process Response
    //.convertBodyTo(String.class)
    // set Auditing Properties
    //.setProperty("processingtype").constant("data")
    //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
    //.setProperty("industrystd").constant("FHIR")
    //.setProperty("messagetrigger").constant("enrollmentrequest")
    //.setProperty("component").simple("${routeId}")
    //.setProperty("processname").constant("Response")
    //.setProperty("camelID").simple("${camelId}")
    //.setProperty("exchangeID").simple("${exchangeId}")
    //.setProperty("internalMsgID").simple("${id}")
    //.setProperty("bodyData").simple("${body}")
    //.setProperty("auditdetails").constant("enrollment response FHIR response message  received")
    // iDAAS DataHub Processing
    //.wireTap("direct:auditing")
    ;

    from("servlet://http://localhost:8888/fhirenrollmentresponse")
            .routeId("FHIREnrollmentresponse")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("enrollmentresponse")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("Enroll Response message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_EnrollmentResponse&brokers=localhost:9092")
    // Invoke External FHIR Server
    //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
    //.to("jetty:http://localhost:8090/fhir-server/api/v4/enrollmentresponse?bridgeEndpoint=true&exchangePattern=InOut")
    //Process Response
    //.convertBodyTo(String.class)
    // set Auditing Properties
    //.setProperty("processingtype").constant("data")
    //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
    //.setProperty("industrystd").constant("FHIR")
    //.setProperty("messagetrigger").constant("enrollmentresponse")
    //.setProperty("component").simple("${routeId}")
    //.setProperty("processname").constant("Response")
    //.setProperty("camelID").simple("${camelId}")
    //.setProperty("exchangeID").simple("${exchangeId}")
    //.setProperty("internalMsgID").simple("${id}")
    //.setProperty("bodyData").simple("${body}")
    //.setProperty("auditdetails").constant("enrollment response FHIR response message received")
    // iDAAS DataHub Processing
    //.wireTap("direct:auditing")
    ;

    from("servlet://http://localhost:8888/fhirexplanationofbenefits")
            .routeId("FHIRExplanationofbenefits")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("explanationofbenefits")
            .setProperty("component").simple("${routeId}")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("processname").constant("Input")
            .setProperty("auditdetails").constant("explanationofbenefits message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_ExplanationOfBenefits&brokers=localhost:9092")
    // Invoke External FHIR Server
    //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
    //.to("jetty:http://localhost:8090/fhir-server/api/v4/explanationofbenefits?bridgeEndpoint=true&exchangePattern=InOut")
    //Process Response
    //.convertBodyTo(String.class)
    // set Auditing Properties
    //.setProperty("processingtype").constant("data")
    //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
    //.setProperty("industrystd").constant("FHIR")
    //.setProperty("messagetrigger").constant("explanationofbenefits")
    //.setProperty("component").simple("${routeId}")
    //.setProperty("processname").constant("Response")
    //.setProperty("camelID").simple("${camelId}")
    //.setProperty("exchangeID").simple("${exchangeId}")
    //.setProperty("internalMsgID").simple("${id}")
    //.setProperty("bodyData").simple("${body}")
    //.setProperty("auditdetails").constant("explanationofbenefits response FHIR response message received")
    // iDAAS DataHub Processing
    //.wireTap("direct:auditing")
    ;
    from("servlet://http://localhost:8888/fhirinsuranceplan")
            .routeId("FHIRInsuranceplan")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("insuranceplan")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("auditdetails").constant("insuranceplan message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_InsurancePlan&brokers=localhost:9092")
    //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
    // Invoke External FHIR Server
    //.to("jetty:http://localhost:8090/fhir-server/api/v4/insuranceplan?bridgeEndpoint=true&exchangePattern=InOut")
    //Process Response
    //.convertBodyTo(String.class)
    // set Auditing Properties
    //.setProperty("processingtype").constant("data")
    //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
    //.setProperty("industrystd").constant("FHIR")
    //.setProperty("messagetrigger").constant("insuranceplan")
    //.setProperty("component").simple("${routeId}")
    //.setProperty("processname").constant("Response")
    //.setProperty("camelID").simple("${camelId}")
    //.setProperty("exchangeID").simple("${exchangeId}")
    //.setProperty("internalMsgID").simple("${id}")
    //.setProperty("bodyData").simple("${body}")
    //.setProperty("auditdetails").constant("insuranceplan FHIR response message received")
    // iDAAS DataHub Processing
    //.wireTap("direct:auditing")
    ;

    from("servlet://http://localhost:8888/fhirclaim")
            .routeId("FHIRClaim")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("claim")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("Claim message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Claim&brokers=localhost:9092")
    // Invoke External FHIR Server
    //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
    //.to("jetty:http://localhost:8090/fhir-server/api/v4/claim?bridgeEndpoint=true&exchangePattern=InOut")
    //Process Response
    //.convertBodyTo(String.class)
    // set Auditing Properties
    //.setProperty("processingtype").constant("data")
    //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
    //.setProperty("industrystd").constant("FHIR")
    //.setProperty("messagetrigger").constant("claim")
    //.setProperty("component").simple("${routeId}")
    //.setProperty("processname").constant("Response")
    //.setProperty("camelID").simple("${camelId}")
    //.setProperty("exchangeID").simple("${exchangeId}")
    //.setProperty("internalMsgID").simple("${id}")
    //.setProperty("bodyData").simple("${body}")
    //.setProperty("auditdetails").constant("claim FHIR Response message received")
    // iDAAS DataHub Processing
    //.wireTap("direct:auditing")
    ;

    from("servlet://http://localhost:8888/fhirclaimresponse")
            .routeId("FHIRClaimresponse")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("claimresponse")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("claimresponse message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_ClaimResponse&brokers=localhost:9092")
    // Invoke External FHIR Server
    //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
    //.to("jetty:http://localhost:8090/fhir-server/api/v4/claimresponse?bridgeEndpoint=true&exchangePattern=InOut")
    //Process Response
    //.convertBodyTo(String.class)
    // set Auditing Properties
    //.setProperty("processingtype").constant("data")
    //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
    //.setProperty("industrystd").constant("FHIR")
    //.setProperty("messagetrigger").constant("claimresponse")
    //.setProperty("component").simple("${routeId}")
    //.setProperty("processname").constant("Response")
    //.setProperty("camelID").simple("${camelId}")
    //.setProperty("exchangeID").simple("${exchangeId}")
    //.setProperty("internalMsgID").simple("${id}")
    //.setProperty("bodyData").simple("${body}")
    //.setProperty("auditdetails").constant("claim response FHIR Reponse message received")
    // iDAAS DataHub Processing
    //.wireTap("direct:auditing")
    ;

    from("servlet://http://localhost:8888/fhirinvoice")
            .routeId("FHIRInvoice")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("invoice")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("invoice message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_Invoice&brokers=localhost:9092")
    // Invoke External FHIR Server
    //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
    //.to("jetty:http://localhost:8090/fhir-server/api/v4/invoice?bridgeEndpoint=true&exchangePattern=InOut")
    //Process Response
    //.convertBodyTo(String.class)
    // set Auditing Properties
    //.setProperty("processingtype").constant("data")
    //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
    //.setProperty("industrystd").constant("FHIR")
    //.setProperty("messagetrigger").constant("invoice")
    //.setProperty("component").simple("${routeId}")
    //.setProperty("processname").constant("Response")
    //.setProperty("camelID").simple("${camelId}")
    //.setProperty("exchangeID").simple("${exchangeId}")
    //.setProperty("internalMsgID").simple("${id}")
    //.setProperty("bodyData").simple("${body}")
    //.setProperty("auditdetails").constant("invoice FHIR message response message received")
    // iDAAS DataHub Processing
    //.wireTap("direct:auditing")
    ;

    from("servlet://http://localhost:8888/fhirpaymentnotice")
            .routeId("FHIRPaymentnotice")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("paymentnotice")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("camelID").simple("${camelId}")
            .setProperty("exchangeID").simple("${exchangeId}")
            .setProperty("internalMsgID").simple("${id}")
            .setProperty("bodyData").simple("${body}")
            .setProperty("auditdetails").constant("paymentnotice message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_PaymentNotice&brokers=localhost:9092")
    // Invoke External FHIR Server
    //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
    //.to("jetty:http://localhost:8090/fhir-server/api/v4/paymentnotice?bridgeEndpoint=true&exchangePattern=InOut")
    //Process Response
    //.convertBodyTo(String.class)
    // set Auditing Properties
    //.setProperty("processingtype").constant("data")
    //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
    //.setProperty("industrystd").constant("FHIR")
    //.setProperty("messagetrigger").constant("paymentnotice")
    //.setProperty("component").simple("${routeId}")
    //.setProperty("processname").constant("Response")
    //.setProperty("camelID").simple("${camelId}")
    //.setProperty("exchangeID").simple("${exchangeId}")
    //.setProperty("internalMsgID").simple("${id}")
    //.setProperty("bodyData").simple("${body}")
    //.setProperty("auditdetails").constant("paymentnotice FHIR response message received")
    // iDAAS DataHub Processing
    //.wireTap("direct:auditing")
    ;

    from("servlet://http://localhost:8888/fhirpaymentreconciliation")
            .routeId("FHIRPaymentreconciliation")
            // set Auditing Properties
            .setProperty("processingtype").constant("data")
            .setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
            .setProperty("industrystd").constant("FHIR")
            .setProperty("messagetrigger").constant("paymentreconciliation")
            .setProperty("component").simple("${routeId}")
            .setProperty("processname").constant("Input")
            .setProperty("auditdetails").constant("paymentreconciliation message received")
            // iDAAS DataHub Processing
            .wireTap("direct:auditing")
            // Send To Topic
            .convertBodyTo(String.class).to("kafka://localhost:9092?topic=FHIRSvr_PaymentReconciliation&brokers=localhost:9092")
    // Invoke External FHIR Server
    //.setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
    //.to("jetty:http://localhost:8090/fhir-server/api/v4/paymentreconciliation?bridgeEndpoint=true&exchangePattern=InOut")
    //Process Response
    //.convertBodyTo(String.class)
    // set Auditing Properties
    //.setProperty("processingtype").constant("data")
    //.setProperty("appname").constant("iDAAS-ConnectFinancial-IndustryStd")
    //.setProperty("industrystd").constant("FHIR")
    //.setProperty("messagetrigger").constant("paymentreconciliation")
    //.setProperty("component").simple("${routeId}")
    //.setProperty("processname").constant("Response")
    //.setProperty("camelID").simple("${camelId}")
    //.setProperty("exchangeID").simple("${exchangeId}")
    //.setProperty("internalMsgID").simple("${id}")
    //.setProperty("bodyData").simple("${body}")
    //.setProperty("auditdetails").constant("paymentreconciliation FHIR response message received")
    // iDAAS DataHub Processing
    //.wireTap("direct:auditing")
    ;

  }
}