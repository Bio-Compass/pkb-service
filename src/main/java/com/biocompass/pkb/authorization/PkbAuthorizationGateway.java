package com.biocompass.pkb.authorization;

@FunctionalInterface
public interface PkbAuthorizationGateway {

    PkbAuthorizationDecision decide(PkbAuthorizationRequest request);
}
