package mpashkovskiy.oasgenerator.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@AllArgsConstructor
@Getter
public class RequestResponse {
    private HttpServletRequest request;
    private HttpServletResponse response;
}
