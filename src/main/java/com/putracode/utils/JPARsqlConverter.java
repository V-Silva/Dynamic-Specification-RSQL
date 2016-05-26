package com.putracode.utils;

import cz.jirutka.rsql.parser.ast.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.Attribute;
import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cz.jirutka.rsql.parser.ast.RSQLOperators.*;

/**
 * Created by KrisnaPutra on 5/23/2016.
 */
public class JPARsqlConverter implements RSQLVisitor<Predicate, Root>  {
    private final CriteriaBuilder builder;
    private final ConversionService conversionService = new DefaultConversionService();


    public JPARsqlConverter(CriteriaBuilder builder) {
        this.builder = builder;
    }


    public Predicate visit(AndNode node, Root root) {

        return builder.and(processNodes(node.getChildren(), root));
    }

    public Predicate visit(OrNode node, Root root) {

        return builder.or(processNodes(node.getChildren(), root));
    }

    public Predicate visit(ComparisonNode node, Root root) {

        ComparisonOperator op = node.getOperator();
        Path attrPath = root.get(node.getSelector());
        Attribute attribute = root.getModel().getAttribute(node.getSelector());
        Class type = attribute.getJavaType();
        if(node.getArguments().size()>1){
            /**
             * Implementasi List
             */
              List<Object> listObject=new ArrayList<>();
            for(String argument:node.getArguments()){
                listObject.add(castDynamicClass(type,argument));
            }
            if(op.equals(IN)){
                return attrPath.in(listObject);
            }else{
                return builder.not(attrPath.in(listObject));
            }

        }else{
            /**
             * Searching With One Value
             */
            Object argument = castDynamicClass(type,node.getArguments().get(0));
            if (op.equals(EQUAL)) {
                if (type.equals(String.class) && argument.toString().contains("*")) {
                    return builder.like(attrPath, argument.toString().replace('*', '%'));
                } else if (argument == null) {
                    return builder.isNull(attrPath);
                } else {
                    return builder.equal(attrPath, argument);
                }
            }
            if (op.equals(NOT_EQUAL)) {
                if (type.equals(String.class)  && argument.toString().contains("*")) {
                    return builder.notLike(attrPath, argument.toString().replace('*', '%'));
                } else if (argument == null) {
                    return builder.isNotNull(attrPath);
                } else {
                    return builder.notEqual(attrPath, argument);
                }
            }
            if (! Comparable.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException(String.format(
                        "Operator %s can be used only for Comparables", op));
            }
            Comparable comparable = (Comparable) conversionService.convert(argument, type);

            if (op.equals(GREATER_THAN)) {
                return builder.greaterThan(attrPath, comparable);
            }
            if (op.equals(GREATER_THAN_OR_EQUAL)) {
                return builder.greaterThanOrEqualTo(attrPath, comparable);
            }
            if (op.equals(LESS_THAN)) {
                return builder.lessThan(attrPath, comparable);
            }
            if (op.equals(LESS_THAN_OR_EQUAL)) {
                return builder.lessThanOrEqualTo(attrPath, comparable);
            }
        }
        throw new IllegalArgumentException("Unknown operator: " + op);
    }

    private Predicate[] processNodes(List<Node> nodes, Root root) {

        Predicate[] predicates = new Predicate[nodes.size()];

        for (int i = 0; i < nodes.size(); i++) {
            predicates[i] = nodes.get(i).accept(this, root);
        }
        return predicates;
    }
    public Object castDynamicClass(Class dynamicClass, String value){
        Object object=null;
        try{
            if(dynamicClass.equals(LocalDate.class)){
                object=LocalDate.parse(value);
            }else if(dynamicClass.equals(LocalDateTime.class)){
                object=LocalDateTime.parse(value);
            }else if(dynamicClass.equals(Character.class)){
                object= (StringUtils.isNotEmpty(value) ? value.charAt(0) :null );
            }
            else{
                Constructor<?> cons =(Constructor<?>) dynamicClass.getConstructor(new Class<?>[]{String.class});
                 object = cons.newInstance(new Object[]{value});
            }

            return object;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

}
