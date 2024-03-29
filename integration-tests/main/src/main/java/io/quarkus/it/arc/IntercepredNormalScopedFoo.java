package io.quarkus.it.arc;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;

import io.quarkus.arc.Lock;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@Unremovable
@Lock
@ApplicationScoped
public class IntercepredNormalScopedFoo {

    private int val;

    public int ping() {
        return val;
    }

    @PostConstruct
    void init() {
        val = 42;
    }

}
