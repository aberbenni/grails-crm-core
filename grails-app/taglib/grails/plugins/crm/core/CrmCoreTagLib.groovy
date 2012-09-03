/*
 * Copyright 2012 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugins.crm.core

class CrmCoreTagLib {

    static namespace = "crm"

    def pluginManager
    def groovyPagesTemplateEngine
    def crmPluginService
    def crmSecurityService

    def noUser = {attrs, body ->
        def principal = attrs.username ? crmSecurityService.getUserInfo(attrs.username) : crmSecurityService.getCurrentUser()
        if (!principal) {
            out << body()
        }
    }

    def user = {attrs, body ->
        def principal = attrs.username ? crmSecurityService.getUserInfo(attrs.username) : crmSecurityService.getCurrentUser()
        if (principal) {
            out << body(principal as Map)
        }
    }

    def noTenant = {attrs, body ->
        def tenant = crmSecurityService.currentTenant
        if (!tenant) {
            out << body()
        }
    }

    def tenant = {attrs, body ->
        def tenant = crmSecurityService.currentTenant
        if (tenant) {
            out << body(tenant as Map)
        }
    }

    def eachTenant = {attrs, body ->
        def list = crmSecurityService.getTenants()
        list.eachWithIndex {s, i ->
            def map = [(attrs.var ?: 'it'): s]
            if (attrs.status) {
                map[attrs.status] = i
            }
            out << body(map)
        }
    }

    def hasPermission = {attrs, body ->
        def perm = attrs.permission
        if (!perm) {
            throwTagError("Tag [hasPermission] is missing required attribute [permission]")
        }
        if (crmSecurityService.isPermitted(perm)) {
            out << body()
        }
    }

    def hasPlugin = {attrs, body ->
        def plugin = attrs.name
        if (!plugin) {
            out << "Tag [hasPlugin] missing required attribute [name]"
            return
        }
        if (pluginManager.hasGrailsPlugin(plugin)) {
            out << body()
        }
    }

    def formatBytes = {attrs ->
        def b = attrs.value
        if (b == null) {
            throwTagError("Tag [formatBytes] is missing required attribute [value]")
        }
        if (!(b instanceof Number)) {
            b = Integer.valueOf(b.toString())
        }
        out << WebUtils.bytesFormatted(b)
    }

    def decorate = {attrs, body ->
        def result = WebUtils.decorateText(body().toString().trim(), attrs.max ? Integer.valueOf(attrs.max) : 0)
        if (attrs.encode) {
            result = result."encodeAs${attrs.encode}"()
        } else {
            result = result.encodeAsHTML()
        }
        out << result
    }

    def pluginViews = {attrs, body ->

        def location = attrs.location
        if (!location) {
            out << "Tag [pluginViews] missing required attribute [location]"
            return
        }

        def views = crmPluginService.getViews(controllerName, actionName, location).sort {it.index ?: (it.id ?: 99999)}

        for (view in views) {
            def params = [:]
            params.putAll(view)

            // isVisible is closure or null/false
            def isVisible = view['visible']
            def closureDelegate
            if (isVisible instanceof Closure) {
                isVisible = isVisible.clone()
                if (closureDelegate == null) {
                    closureDelegate = new ClosureDelegate(delegate, grailsApplication, pageScope.getVariables(), [
                            session: session,
                            request: request,
                            controllerName: controllerName,
                            actionName: actionName,
                            flash: flash,
                            params: params
                    ])
                }
                isVisible.delegate = closureDelegate
                isVisible.resolveStrategy = Closure.DELEGATE_FIRST
            }

            if ((isVisible == null) || (isVisible == true) || isVisible.call()) {
                def model = params.model
                if (model instanceof Closure) {
                    def cl = model.clone()
                    if (closureDelegate == null) {
                        closureDelegate = new ClosureDelegate(delegate, grailsApplication, pageScope.getVariables(), [
                                session: session,
                                request: request,
                                controllerName: controllerName,
                                actionName: actionName,
                                flash: flash,
                                params: params
                        ])
                    }
                    cl.delegate = closureDelegate
                    cl.resolveStrategy = Closure.DELEGATE_FIRST
                    params.model = cl()
                }
                out << body([(attrs.var ?: 'it'): params])
            }
        }
    }

    def tenantOption = {attrs, body ->
        def option = attrs.name
        if (!option) {
            out << "Tag [tenantOption] missing required attribute [name]"
            return
        }
        def id = attrs.tenant ? Long.valueOf(attrs.tenant.toString()) : TenantUtils.tenant
        def tenant = crmSecurityService.getTenantInfo(id)
        if (tenant) {
            def value = tenant.options[option]
            def render = false
            if (attrs.eq && (attrs.eq == value)) {
                render = true
            } else if (attrs.ne && (attrs.ne != value)) {
                render = true
            } else if (value) {
                render = true
            }
            if (render) {
                out << body()
            }
        }
    }
}
