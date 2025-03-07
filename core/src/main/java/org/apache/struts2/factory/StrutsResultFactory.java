/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.struts2.factory;

import com.opensymphony.xwork2.ObjectFactory;
import com.opensymphony.xwork2.Result;
import com.opensymphony.xwork2.config.ConfigurationException;
import com.opensymphony.xwork2.config.entities.ResultConfig;
import com.opensymphony.xwork2.factory.ResultFactory;
import com.opensymphony.xwork2.inject.Inject;
import com.opensymphony.xwork2.result.ParamNameAwareResult;
import com.opensymphony.xwork2.util.reflection.ReflectionException;
import com.opensymphony.xwork2.util.reflection.ReflectionExceptionHandler;
import com.opensymphony.xwork2.util.reflection.ReflectionProvider;

import java.util.Map;

/**
 * Default implementation which uses {@link com.opensymphony.xwork2.result.ParamNameAwareResult} to accept or throw away parameters
 */
public class StrutsResultFactory implements ResultFactory {

    protected ObjectFactory objectFactory;
    protected ReflectionProvider reflectionProvider;

    @Inject
    public void setObjectFactory(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    @Inject
    public void setReflectionProvider(ReflectionProvider provider) {
        this.reflectionProvider = provider;
    }

    public Result buildResult(ResultConfig resultConfig, Map<String, Object> extraContext) throws Exception {
        String resultClassName = resultConfig.getClassName();
        Result result = null;

        if (resultClassName != null) {
            Object o = objectFactory.buildBean(resultClassName, extraContext);
            Map<String, String> params = resultConfig.getParams();
            if (params != null) {
                setParameters(extraContext, o, params);
            }
            if (o instanceof Result) {
                result = (Result) o;
            } else if (o instanceof org.apache.struts2.Result) {
                result = Result.adapt((org.apache.struts2.Result) o);
            }
            if (result == null) {
                throw new ConfigurationException("Class [" + resultClassName + "] does not implement Result", resultConfig);
            }
        }
        return result;
    }

    protected void setParameters(Map<String, Object> extraContext, Result result, Map<String, String> params) {
        setParametersHelper(extraContext, result, params);
    }

    protected void setParameters(Map<String, Object> extraContext, Object result, Map<String, String> params) {
        if (result instanceof Result) {
            setParameters(extraContext, (Result) result, params);
        } else {
            setParametersHelper(extraContext, result, params);
        }
    }

    private void setParametersHelper(Map<String, Object> extraContext, Object result, Map<String, String> params) {
        for (Map.Entry<String, String> paramEntry : params.entrySet()) {
            try {
                String name = paramEntry.getKey();
                String value = paramEntry.getValue();
                setParameter(result, name, value, extraContext);
            } catch (ReflectionException ex) {
                if (result instanceof ReflectionExceptionHandler) {
                    ((ReflectionExceptionHandler) result).handle(ex);
                }
            }
        }
    }

    protected void setParameter(Result result, String name, String value, Map<String, Object> extraContext) {
        setParameterHelper(result, name, value, extraContext);
    }

    private void setParameter(Object result, String name, String value, Map<String, Object> extraContext) {
        if (result instanceof Result) {
            setParameter((Result) result, name, value, extraContext);
        } else {
            setParameterHelper(result, name, value, extraContext);
        }
    }

    private void setParameterHelper(Object result, String name, String value, Map<String, Object> extraContext) {
        if (result instanceof ParamNameAwareResult) {
            if (((ParamNameAwareResult) result).acceptableParameterName(name, value)) {
                reflectionProvider.setProperty(name, value, result, extraContext, true);
            }
        } else {
            reflectionProvider.setProperty(name, value, result, extraContext, true);
        }
    }
}
