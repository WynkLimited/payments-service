package in.wynk.payment.service;

import in.wynk.payment.dto.UrlShortenRequest;
import in.wynk.payment.dto.UrlShortenResponse;

public interface IUrlShortenService {

    UrlShortenResponse generate(UrlShortenRequest request);

}
