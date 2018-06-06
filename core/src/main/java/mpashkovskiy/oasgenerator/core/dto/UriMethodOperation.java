package mpashkovskiy.oasgenerator.core.dto;

import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class UriMethodOperation {
    private String uri;
    private HttpMethod method;
    private Operation op;
}
