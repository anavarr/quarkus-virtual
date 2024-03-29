package io.quarkus.jaxrs.client.reactive.runtime;

import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;

public abstract class RestClientBase implements Closeable {
    private final List<ParamConverterProvider> paramConverterProviders;
    private final Map<Class<?>, ParamConverterProvider> providerForClass = new ConcurrentHashMap<>();

    public RestClientBase(List<ParamConverterProvider> providers) {
        this.paramConverterProviders = providers;
    }

    @SuppressWarnings("unused") // used by generated code
    public <T> Object[] convertParamArray(T[] value, Class<T> type, Type genericType, Annotation[] annotations) {
        ParamConverter<T> converter = getConverter(type, genericType, annotations);

        if (converter == null) {
            return value;
        } else {
            Object[] result = new Object[value.length];

            for (int i = 0; i < value.length; i++) {
                result[i] = converter.toString(value[i]);
            }
            return result;
        }
    }

    @SuppressWarnings("unused") // used by generated code
    public <T> Object convertParam(T value, Class<T> type, Type genericType, Annotation[] annotations) {
        ParamConverter<T> converter = getConverter(type, genericType, annotations);
        if (converter != null) {
            return converter.toString(value);
        } else {
            return value;
        }
    }

    private <T> ParamConverter<T> getConverter(Class<T> type, Type genericType, Annotation[] annotations) {
        ParamConverterProvider converterProvider = providerForClass.get(type);

        if (converterProvider == null) {
            for (ParamConverterProvider provider : paramConverterProviders) {
                ParamConverter<T> converter = provider.getConverter(type, genericType, annotations);
                if (converter != null) {
                    providerForClass.put(type, provider);
                    return converter;
                }
            }
            providerForClass.put(type, NO_PROVIDER);
        } else if (converterProvider != NO_PROVIDER) {
            return converterProvider.getConverter(type, genericType, annotations);
        }
        return null;
    }

    private static final ParamConverterProvider NO_PROVIDER = new ParamConverterProvider() {
        @Override
        public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
            return null;
        }
    };
}
