package in.org.projecteka.hiu.dataflow;

import in.org.projecteka.hiu.consent.ConsentRepository;
import in.org.projecteka.hiu.consent.model.ConsentStatus;
import in.org.projecteka.hiu.dataflow.model.DataEntry;
import in.org.projecteka.hiu.dataprocessor.model.EntryStatus;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import static in.org.projecteka.hiu.ClientError.unauthorized;
import static in.org.projecteka.hiu.ClientError.unauthorizedRequester;
import static in.org.projecteka.hiu.ClientError.consentArtefactGone;

@AllArgsConstructor
public class HealthInfoManager {
    private final ConsentRepository consentRepository;
    private final DataFlowRepository dataFlowRepository;
    private final HealthInformationRepository healthInformationRepository;
    private static final Logger logger = LoggerFactory.getLogger(HealthInfoManager.class);

    public Flux<DataEntry> fetchHealthInformation(String consentRequestId, String requesterId) {
        return consentRepository.getConsentDetails(consentRequestId)
                .filter(consentDetail -> isValidRequester(requesterId, consentDetail))
                .switchIfEmpty(Flux.error(unauthorizedRequester()))
                .filter(this::isGrantedConsent)
                .switchIfEmpty(Flux.error(unauthorized()))
                .filter(this::isConsentNotExpired)
                .switchIfEmpty(Flux.error(consentArtefactGone()))
                .flatMap(consentDetail -> dataFlowRepository.getTransactionId(consentDetail.get("consentId"))
                        .flatMapMany(transactionId -> getDataEntries(
                                transactionId,
                                consentDetail.get("hipId"),
                                consentDetail.get("hipName"))));
    }

    public String getTransactionIdForConsentRequest(String consentRequestId) {
        return consentRepository.getConsentArtefactId(consentRequestId)
                .flatMap(dataFlowRepository::getTransactionId).block();
    }

    private boolean isConsentNotExpired(Map<String, String> consentDetail) {
        return !hasConsentArtefactExpired(consentDetail.get("consentExpiryDate"));
    }

    private boolean hasConsentArtefactExpired(String dataEraseAt) {
        Date expiryDate = null;
        Date today = new Date();
        expiryDate = toDate(dataEraseAt);
        return !(expiryDate != null && (expiryDate.after(today) || expiryDate.equals(today)));
    }

    private Date toDate(String dateExpiryAt) {
        try {
            var withMillSeconds = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'+0000'");
            return withMillSeconds.parse(dateExpiryAt);
        } catch (ParseException e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    private boolean isGrantedConsent(Map<String, String> consentDetail) {
        return consentDetail.get("status").equals(ConsentStatus.GRANTED.toString());
    }

    private boolean isValidRequester(String requesterId, Map<String, String> consentDetail) {
        return consentDetail.get("requester").equals(requesterId);
    }

    private Flux<DataEntry> getDataEntries(String transactionId, String hipId, String hipName) {
        return healthInformationRepository.getHealthInformation(transactionId)
                .map(healthInfo -> DataEntry.builder()
                        .hipId(hipId)
                        .hipName(hipName)
                        .status(toStatus((String) healthInfo.get("status")))
                        .data(healthInfo.get("data"))
                        .build());
    }

    private EntryStatus toStatus(String status) {
        if ((status != null) && !"".equals(status)) {
            return EntryStatus.valueOf(status);
        }
        return null;
    }
}
