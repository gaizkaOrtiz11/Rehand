package com.rehand.exception;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Turns expected failures into a flash message on the previous page.
 *
 * <ul>
 *   <li>{@link BusinessRuleException}: message is shown as {@code flashError}.</li>
 *   <li>{@link MaxUploadSizeExceededException}: friendly "file too large" message.</li>
 * </ul>
 *
 * Anything else falls through to Spring Boot's error handling, which renders
 * {@code templates/error/404.html}, {@code 403.html}, {@code 500.html} or {@code error.html}.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    public static final String FLASH_ERROR = "flashError";

    @ExceptionHandler(BusinessRuleException.class)
    public String handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return redirectBack(request, ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return redirectBack(request, "That file is too large. Photos can be up to 5 MB each.");
    }

    private String redirectBack(HttpServletRequest request, String message) {
        RequestContextUtils.getOutputFlashMap(request).put(FLASH_ERROR, message);
        return "redirect:" + previousPage(request);
    }

    /** Path (and query) of the Referer when it points at this host, otherwise the home page. */
    private String previousPage(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return "/";
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase(request.getServerName())) {
                return "/";
            }
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            if (!path.startsWith("/") || path.startsWith("//")) {
                return "/"; // "//host/..." would be read as another site by the browser (open redirect)
            }
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }
}
