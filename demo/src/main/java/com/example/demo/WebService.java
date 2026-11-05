
package com.example.demo;

import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.*;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Component
@Path("/service")
public class WebService {

  // WS1 : Recherche du citoyen
  private static final String SERVICE1_URI =
          "http://localhost:8081/service";

  // WS2 : Enregistrement du nouveau-ne
  private static final String SERVICE2_URI =
          "http://localhost:8082/service";

  // Service SOAP : Notification
  private static final String SOAP_URI =
          "http://localhost:8086/NotificationService/services/NotificationImplPort";

  private final Client client = ClientBuilder.newBuilder()
          .register(JacksonFeature.class)
          .build();

  @PreDestroy
  void fermer() {
    client.close();
  }

  @POST
  @Path("/inscriptions/{cin}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Map<String, Object> inscrire(
          @PathParam("cin") String cin,
          NouveauNe nne) {

    if (nne == null || nne.getPrenomNouveauN() == null
            || nne.getPrenomNouveauN().isBlank()) {

      throw erreur(400, "Prénom du nouveau-né obligatoire");
    }

    return composer(cin, nne);
  }

  // ---------- Service composé ----------
  private Map<String, Object> composer(String cin, NouveauNe nne) {

    // 1. Appel du WS1 : recherche du citoyen
    Citoyen citoyen;

    try (Response r1 = client.target(SERVICE1_URI)
            .path("citoyens")
            .path(cin)
            .request(MediaType.APPLICATION_JSON)
            .get()) {

      if (r1.getStatus() == 404) {
        throw erreur(404, "CIN introuvable : " + cin);
      }

      if (r1.getStatus() != 200) {
        throw erreur(502, "Erreur du Web Service 1");
      }

      citoyen = r1.readEntity(Citoyen.class);

    } catch (ProcessingException e) {
      throw erreur(503, "Web Service 1 indisponible");
    }

    // 2. Compléter les informations du nouveau-ne
    nne.setCinPere(citoyen.getCin());
    nne.setNomPere(citoyen.getNom());
    nne.setPrenomPere(citoyen.getPrenomPere());

    // 3. Appel du WS2 : enregistrer le nouveau-ne
    NouveauNe cree;

    try (Response r2 = client.target(SERVICE2_URI)
            .path("nouveaux_nes")
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.json(nne))) {

      if (r2.getStatus() != 201) {
        throw erreur(502, "Erreur du Web Service 2");
      }

      cree = r2.readEntity(NouveauNe.class);

    } catch (ProcessingException e) {
      throw erreur(503, "Web Service 2 indisponible");
    }

    // 4. Appel du service SOAP : notification
    String enveloppe = """
                <soapenv:Envelope
                    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                    xmlns:not="http://notification/">
                    <soapenv:Header/>
                    <soapenv:Body>
                        <not:notify>
                            <arg0>%s</arg0>
                            <arg1>%s</arg1>
                        </not:notify>
                    </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(cree.getIdNe(), citoyen.getCin());

    String messageSoap;

    try (Response r3 = client.target(SOAP_URI)
            .request(MediaType.TEXT_XML)
            .header("SOAPAction", "\"\"")
            .post(Entity.entity(enveloppe, MediaType.TEXT_XML))) {

      if (r3.getStatus() != 200) {
        throw erreur(502, "Erreur du Web Service SOAP");
      }

      messageSoap = extraireReturn(r3.readEntity(String.class));

    } catch (ProcessingException e) {
      throw erreur(503, "Web Service SOAP indisponible");
    }

    // 5. Construire la réponse finale
    Map<String, Object> resultat = new LinkedHashMap<>();

    resultat.put("nouveauNe", cree);
    resultat.put("message", messageSoap);

    return resultat;
  }

  // Extraire le contenu de la balise <return> de la réponse SOAP
  private String extraireReturn(String xml) {

    try {
      DocumentBuilderFactory dbf =
              DocumentBuilderFactory.newInstance();

      dbf.setFeature(
              XMLConstants.FEATURE_SECURE_PROCESSING, true);

      dbf.setFeature(
              "http://apache.org/xml/features/disallow-doctype-decl",
              true);

      Document doc = dbf.newDocumentBuilder()
              .parse(new InputSource(new StringReader(xml)));

      NodeList noeuds = doc.getElementsByTagName("return");

      return noeuds.getLength() > 0
              ? noeuds.item(0).getTextContent()
              : xml;

    } catch (Exception e) {
      throw erreur(502, "Réponse SOAP invalide");
    }
  }

  // Gestion des erreurs HTTP
  private WebApplicationException erreur(int status, String message) {

    return new WebApplicationException(
            Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of(
                            "statut", "echec",
                            "message", message))
                    .build());
  }
}
