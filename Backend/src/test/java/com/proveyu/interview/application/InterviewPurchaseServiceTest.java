package com.proveyu.interview.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewPurchaseServiceTest {

    private static InterviewPurchaseService service(String upiId) {
        return new InterviewPurchaseService(null, null, null, null, null, new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object t, org.springframework.transaction.TransactionDefinition d) {}
            @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus s) {}
            @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus s) {}
        }, 20, 10, upiId, "ProveYu");
    }

    @Test
    void upiLinkCarriesPayeeNameExactAmountAndReference() {
        String uri = service("proveyu@okaxis").upiUri(4000, "PYAB23CD45");
        assertThat(uri).isEqualTo("upi://pay?pa=proveyu%40okaxis&pn=ProveYu&am=40.00&cu=INR&tn=ProveYu%20PYAB23CD45");
    }

    @Test
    void buyingIsOffUntilAUpiIdIsSet() {
        assertThat(service("").ready()).isFalse();
        assertThat(service("not-an-id").ready()).isFalse();
        assertThat(service("proveyu@okaxis").ready()).isTrue();
    }
}
