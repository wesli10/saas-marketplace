package br.com.saasfinder.shared.error;

import java.util.List;

/** Publicacao barrada por pendencias de completude; cada pendencia vira um item de errors[] (AC-19). */
public class NotPublishableException extends ApiException {

    public NotPublishableException(List<ApiFieldError> pendencias) {
        super(ProblemTypes.NOT_PUBLISHABLE, "problema.not-publishable.detalhe", pendencias);
    }
}
