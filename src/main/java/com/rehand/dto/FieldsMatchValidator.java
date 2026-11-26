package com.rehand.dto;

import java.util.Objects;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

public class FieldsMatchValidator implements ConstraintValidator<FieldsMatch, Object> {

    private String first;
    private String second;
    private String message;

    @Override
    public void initialize(FieldsMatch annotation) {
        this.first = annotation.first();
        this.second = annotation.second();
        this.message = annotation.message();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(value);
        Object a = wrapper.getPropertyValue(first);
        Object b = wrapper.getPropertyValue(second);
        if (a == null || b == null || Objects.equals(a, b)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(second)
                .addConstraintViolation();
        return false;
    }
}
