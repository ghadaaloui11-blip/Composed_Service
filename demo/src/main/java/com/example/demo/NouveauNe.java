package com.example.demo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NouveauNe {
  private Long idNe;
  private String prenomNouveauN;
  private String nomPere;
  private String prenomPere;
  private String cinPere;
  private String dateNaissance;

  public Long getIdNe() { return idNe; }
  public void setIdNe(Long idNe) { this.idNe = idNe; }
  public String getPrenomNouveauN() { return prenomNouveauN; }
  public void setPrenomNouveauN(String v) { this.prenomNouveauN = v; }
  public String getNomPere() { return nomPere; }
  public void setNomPere(String v) { this.nomPere = v; }
  public String getPrenomPere() { return prenomPere; }
  public void setPrenomPere(String v) { this.prenomPere = v; }
  public String getCinPere() { return cinPere; }
  public void setCinPere(String v) { this.cinPere = v; }
  public String getDateNaissance() { return dateNaissance; }
  public void setDateNaissance(String v) { this.dateNaissance = v; }
}
