(function () {
    'use strict';

    var TOKEN_KEY = 'tax_token';
    var USER_KEY = 'tax_user';

    var API = {
        base: 'api',

        token: function () {
            return localStorage.getItem(TOKEN_KEY);
        },

        user: function () {
            try {
                return JSON.parse(localStorage.getItem(USER_KEY) || 'null');
            } catch (e) {
                return null;
            }
        },

        setSession: function (token, user) {
            if (token) {
                localStorage.setItem(TOKEN_KEY, token);
            }
            if (user) {
                localStorage.setItem(USER_KEY, JSON.stringify(user));
            }
        },

        setUser: function (user) {
            localStorage.setItem(USER_KEY, JSON.stringify(user));
        },

        clear: function () {
            localStorage.removeItem(TOKEN_KEY);
            localStorage.removeItem(USER_KEY);
        },

        request: function (method, path, body) {
            var headers = {};
            var token = API.token();
            if (token) {
                headers.Authorization = 'Bearer ' + token;
            }
            var options = { method: method, headers: headers };
            if (body !== undefined && body !== null) {
                headers['Content-Type'] = 'application/json';
                options.body = JSON.stringify(body);
            }
            return fetch(API.base + path, options).then(function (response) {
                return response.text().then(function (text) {
                    var data = null;
                    if (text) {
                        try {
                            data = JSON.parse(text);
                        } catch (e) {
                            data = text;
                        }
                    }
                    if (response.status === 401) {
                        API.clear();
                        if (!/login\.html$/.test(window.location.pathname)) {
                            window.location.replace('login.html');
                        }
                        throw new Error((data && data.message) || '登录已过期，请重新登录');
                    }
                    if (!response.ok) {
                        var message = (data && data.message) || ('请求失败（' + response.status + '）');
                        throw new Error(message);
                    }
                    return data;
                });
            });
        },

        get: function (path) {
            return API.request('GET', path);
        },

        post: function (path, body) {
            return API.request('POST', path, body);
        },

        put: function (path, body) {
            return API.request('PUT', path, body);
        },

        del: function (path) {
            return API.request('DELETE', path);
        }
    };

    window.API = API;
})();
