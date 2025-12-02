package ch.sthomas.stddivelogger.ws.advice;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.zalando.problem.spring.web.advice.ProblemHandling;
import org.zalando.problem.spring.web.advice.security.SecurityAdviceTrait;
import org.zalando.problem.spring.web.advice.validation.ConstraintViolationAdviceTrait;

@RestControllerAdvice
public class ExceptionHandling
        implements ProblemHandling,
                SecurityAdviceTrait,
                NoSuchElementAdviceTrait,
                ConstraintViolationAdviceTrait,
                DiveConstraintAdviceTrait {}
