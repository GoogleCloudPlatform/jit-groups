package com.google.solutions.jitaccess.core.activation;

class SampleEntitlementId extends EntitlementId
{
  private final String catalog;
  private final String id;

  public SampleEntitlementId(String catalog, String id) {
    this.catalog = catalog;
    this.id = id;
  }

  public SampleEntitlementId(String id) {
    this("sample", id);
  }

  @Override
  public String catalog() {
    return this.catalog;
  }

  @Override
  public String id() {
    return this.id;
  }
}