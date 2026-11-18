package com.proveyu.student.interview.application;

import com.proveyu.shared.settings.PlatformSettingRepository;
import com.proveyu.shared.settings.PlatformSettings;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewPurchaseServiceTest {

    /** Settings with application.yml-style defaults and nothing saved by an admin. */
    private static PlatformSettings settings(String upiId) {
        return new PlatformSettings(Mockito.mock(PlatformSettingRepository.class), 20, 10, 15, upiId, "ProveYu", 2, 50, 0);
    }

    private static InterviewPurchaseService service(PlatformSettings settings) {
        return new InterviewPurchaseService(null, null, null, null, null, new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object t, org.springframework.transaction.TransactionDefinition d) {}
            @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus s) {}
            @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus s) {}
        }, settings);
    }

    @Test
    void upiLinkCarriesPayeeNameExactAmountAndReference() {
        String uri = service(settings("proveyu@okaxis")).upiUri(4000, "PYAB23CD45");
        assertThat(uri).isEqualTo("upi://pay?pa=proveyu%40okaxis&pn=ProveYu&am=40.00&cu=INR&tn=ProveYu%20PYAB23CD45");
    }

    @Test
    void buyingIsOffUntilAUpiIdIsSet() {
        assertThat(service(settings("")).ready()).isFalse();
        assertThat(service(settings("not-an-id")).ready()).isFalse();
        assertThat(service(settings("proveyu@okaxis")).ready()).isTrue();
    }

    @Test
    void adminSettingsOverrideTheDefaultsAtOnce() {
        PlatformSettings s = settings("");
        InterviewPurchaseService service = service(s);
        assertThat(service.priceRupees()).isEqualTo(20);
        s.update(Map.of(PlatformSettings.INTERVIEW_PRICE, "35", PlatformSettings.INTERVIEW_UPI_ID, "campus@okhdfc"), UUID.randomUUID());
        assertThat(service.priceRupees()).isEqualTo(35);
        assertThat(service.ready()).isTrue();
        assertThat(service.upiUri(3500, "R1")).startsWith("upi://pay?pa=campus%40okhdfc");
    }
}
