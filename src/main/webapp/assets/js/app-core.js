(function () {
    'use strict';

    var App = window.App = window.App || {};
    App.pages = {};
    App.hooks = {};
    App.state = { user: API.user(), route: 'dashboard' };

    var ICONS = {
        dashboard: '<rect x="3" y="3" width="7" height="9" rx="1.5"></rect><rect x="14" y="3" width="7" height="5" rx="1.5"></rect><rect x="14" y="12" width="7" height="9" rx="1.5"></rect><rect x="3" y="16" width="7" height="5" rx="1.5"></rect>',
        taxpayers: '<path d="M3 21h18"></path><path d="M5 21V7l7-4 7 4v14"></path><path d="M9 9h1"></path><path d="M14 9h1"></path><path d="M9 13h1"></path><path d="M14 13h1"></path><path d="M10 21v-4h4v4"></path>',
        declarations: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="8" y1="13" x2="16" y2="13"></line><line x1="8" y1="17" x2="14" y2="17"></line>',
        audit: '<path d="M9 11l3 3L22 4"></path><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path>',
        payment: '<rect x="2" y="5" width="20" height="14" rx="2"></rect><line x1="2" y1="10" x2="22" y2="10"></line><line x1="6" y1="15" x2="10" y2="15"></line>',
        invoice: '<path d="M4 3h16v18l-3-2-2 2-3-2-3 2-2-2-3 2z"></path><line x1="8" y1="8" x2="16" y2="8"></line><line x1="8" y1="12" x2="16" y2="12"></line><line x1="8" y1="16" x2="13" y2="16"></line>',
        query: '<circle cx="11" cy="11" r="7"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line>',
        tax: '<line x1="19" y1="5" x2="5" y2="19"></line><circle cx="6.5" cy="6.5" r="2.5"></circle><circle cx="17.5" cy="17.5" r="2.5"></circle>',
        users: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><path d="M23 21v-2a4 4 0 0 0-3-3.87"></path><path d="M16 3.13a4 4 0 0 1 0 7.75"></path>',
        logs: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="8" y1="13" x2="16" y2="13"></line><line x1="8" y1="17" x2="16" y2="17"></line>',
        plus: '<line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line>',
        refresh: '<polyline points="23 4 23 10 17 10"></polyline><polyline points="1 20 1 14 7 14"></polyline><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10"></path><path d="M20.49 15a9 9 0 0 1-14.85 3.36L1 14"></path>',
        download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line>',
        eye: '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle>',
        edit: '<path d="M12 20h9"></path><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4z"></path>',
        trash: '<polyline points="3 6 5 6 21 6"></polyline><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"></path><path d="M10 11v6"></path><path d="M14 11v6"></path><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"></path>',
        check: '<polyline points="20 6 9 17 4 12"></polyline>',
        close: '<line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line>',
        send: '<line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>',
        card: '<rect x="2" y="5" width="20" height="14" rx="2"></rect><line x1="2" y1="10" x2="22" y2="10"></line>',
        logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line>',
        key: '<path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"></path>',
        info: '<circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line>',
        inbox: '<polyline points="22 12 16 12 14 15 10 15 8 12 2 12"></polyline><path d="M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"></path>',
        clock: '<circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline>',
        arrow: '<line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline>'
    };

    var DECLARATION_CLASS = {
        '草稿': 'gray',
        '待审核': 'orange',
        '已通过': 'blue',
        '已驳回': 'red',
        '已缴款': 'green'
    };

    var TAXPAYER_CLASS = {
        '正常': 'green',
        '停业': 'orange',
        '注销': 'gray'
    };

    var USER_CLASS = {
        '启用': 'green',
        '停用': 'gray'
    };

    var INVOICE_CLASS = {
        '正常': 'green',
        '作废': 'gray'
    };

    var ROLE_LABEL = {
        ADMIN: '系统管理员',
        AUDITOR: '审核人员',
        TAXPAYER: '纳税人'
    };

    var ROLE_CLASS = {
        ADMIN: 'purple',
        AUDITOR: 'blue',
        TAXPAYER: 'green'
    };

    var NAV = [
        {
            group: '业务办理',
            items: [
                { key: 'dashboard', title: '工作台', icon: 'dashboard' },
                { key: 'declarations', title: '纳税申报', icon: 'declarations' },
                { key: 'audits', title: '申报审核', icon: 'audit', roles: ['ADMIN', 'AUDITOR'] },
                { key: 'payments', title: '税款缴纳', icon: 'payment' },
                { key: 'invoices', title: '发票管理', icon: 'invoice' }
            ]
        },
        {
            group: '基础管理',
            items: [
                { key: 'taxpayers', title: '纳税人管理', icon: 'taxpayers', roles: ['ADMIN', 'AUDITOR'] },
                { key: 'tax-types', title: '税种管理', icon: 'tax', roles: ['ADMIN'] },
                { key: 'users', title: '用户管理', icon: 'users', roles: ['ADMIN'] }
            ]
        },
        {
            group: '综合应用',
            items: [
                { key: 'query', title: '综合查询', icon: 'query' },
                { key: 'logs', title: '操作日志', icon: 'logs', roles: ['ADMIN'] }
            ]
        }
    ];

    function valueOf(value) {
        return value === null || value === undefined ? '' : String(value);
    }

    function hasRole(roles, role) {
        return !roles || roles.indexOf(role) >= 0;
    }

    function navItem(key) {
        for (var i = 0; i < NAV.length; i++) {
            for (var j = 0; j < NAV[i].items.length; j++) {
                if (NAV[i].items[j].key === key) {
                    return NAV[i].items[j];
                }
            }
        }
        return null;
    }

    function statusBadge(text, map) {
        var cls = map[text] || 'gray';
        return '<span class="badge ' + cls + '">' + App.esc(text || '—') + '</span>';
    }

    App.icon = function (name, size) {
        return '<svg width="' + (size || 18) + '" height="' + (size || 18) + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">' + (ICONS[name] || '') + '</svg>';
    };

    App.esc = function (value) {
        return valueOf(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    };

    App.num = function (value, digits) {
        if (value === null || value === undefined || value === '') {
            return '0';
        }
        var number = Number(value);
        if (isNaN(number)) {
            return App.esc(value);
        }
        return number.toLocaleString('zh-CN', {
            minimumFractionDigits: digits === undefined ? 2 : digits,
            maximumFractionDigits: digits === undefined ? 2 : digits
        });
    };

    App.money = function (value) {
        return '¥ ' + App.num(value, 2);
    };

    App.rate = function (value) {
        var number = Number(value);
        if (isNaN(number)) {
            return valueOf(value);
        }
        return (Math.round(number * 10000) / 10000) + '%';
    };

    App.dateOnly = function (value) {
        return value ? valueOf(value).slice(0, 10) : '—';
    };

    App.dateTime = function (value) {
        return value ? valueOf(value).slice(0, 16) : '—';
    };

    App.roleLabel = function (role) {
        return ROLE_LABEL[role] || role || '—';
    };

    App.roleBadge = function (role) {
        return '<span class="badge ' + (ROLE_CLASS[role] || 'gray') + '">' + App.esc(App.roleLabel(role)) + '</span>';
    };

    App.declarationBadge = function (status) {
        return statusBadge(status, DECLARATION_CLASS);
    };

    App.taxpayerBadge = function (status) {
        return statusBadge(status, TAXPAYER_CLASS);
    };

    App.userStatusBadge = function (status) {
        return statusBadge(status, USER_CLASS);
    };

    App.invoiceBadge = function (status) {
        return statusBadge(status, INVOICE_CLASS);
    };

    App.badge = statusBadge;

    App.query = function (params) {
        var parts = [];
        Object.keys(params || {}).forEach(function (key) {
            var value = params[key];
            if (value !== undefined && value !== null && value !== '') {
                parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(value));
            }
        });
        return parts.length ? '?' + parts.join('&') : '';
    };

    App.loading = function (text) {
        return '<div class="loading"><div class="spinner"></div><span>' + App.esc(text || '正在加载数据...') + '</span></div>';
    };

    App.empty = function (title, text) {
        return '<div class="empty"><div class="empty-icon">' + App.icon('inbox', 26) + '</div><strong>' + App.esc(title || '暂无数据') + '</strong><p>' + App.esc(text || '换个筛选条件试试') + '</p></div>';
    };

    App.errorBox = function (error) {
        return '<div class="error-box"><div class="empty-icon">' + App.icon('info', 26) + '</div><strong>加载失败</strong><p>' + App.esc((error && error.message) || '请稍后重试') + '</p></div>';
    };

    App.showError = function (container, error) {
        container.innerHTML = App.errorBox(error);
    };

    App.pager = function (result) {
        var total = Number(result.total || 0);
        var page = Number(result.page || 1);
        var size = Number(result.size || 10);
        var pages = Math.max(1, Math.ceil(total / size));
        var start = total === 0 ? 0 : (page - 1) * size + 1;
        var end = Math.min(total, page * size);
        return '<div class="pager" data-pager>' +
            '<span>共 ' + total + ' 条 · ' + start + '-' + end + ' · 第 ' + page + ' / ' + pages + ' 页</span>' +
            '<button type="button" data-page="' + (page - 1) + '"' + (page <= 1 ? ' disabled' : '') + '>上一页</button>' +
            '<span class="page-info">' + page + '</span>' +
            '<button type="button" data-page="' + (page + 1) + '"' + (page >= pages ? ' disabled' : '') + '>下一页</button>' +
            '</div>';
    };

    App.bindPager = function (scope, result, callback) {
        var pager = scope.querySelector('[data-pager]');
        if (!pager) {
            return;
        }
        pager.querySelectorAll('button[data-page]').forEach(function (button) {
            button.addEventListener('click', function () {
                var target = Number(button.getAttribute('data-page'));
                var pages = Math.max(1, Math.ceil(Number(result.total || 0) / Number(result.size || 10)));
                if (target >= 1 && target <= pages && target !== Number(result.page)) {
                    callback(target);
                }
            });
        });
    };

    App.toast = function (message, type) {
        var root = document.getElementById('toast-root');
        var kind = type || 'success';
        var icon = kind === 'error' ? '!' : (kind === 'info' ? 'i' : '✓');
        var toast = document.createElement('div');
        toast.className = 'toast ' + kind;
        toast.innerHTML = '<span class="t-icon">' + icon + '</span><div class="t-body">' + App.esc(message) + '</div>';
        root.appendChild(toast);
        window.setTimeout(function () {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 3600);
    };

    App.openModal = function (options) {
        App.closeModal();
        var root = document.getElementById('modal-root');
        var footer = options.footer ? '<div class="modal-foot">' + options.footer + '</div>' : '';
        root.innerHTML = '<div class="modal-backdrop"><div class="modal' + (options.wide ? ' wide' : '') + '" role="dialog" aria-modal="true">' +
            '<div class="modal-head"><h3>' + App.esc(options.title || '') + '</h3><button type="button" class="modal-close" data-modal-close aria-label="关闭">&times;</button></div>' +
            '<div class="modal-body">' + (options.body || '') + '</div>' + footer + '</div></div>';
        var backdrop = root.firstElementChild;
        var modal = backdrop.querySelector('.modal');

        function onKey(event) {
            if (event.key === 'Escape') {
                close();
            }
        }

        function close() {
            document.body.classList.remove('modal-open');
            root.innerHTML = '';
            document.removeEventListener('keydown', onKey);
            App._closeModal = null;
        }

        backdrop.querySelector('[data-modal-close]').addEventListener('click', close);
        backdrop.addEventListener('mousedown', function (event) {
            if (event.target === backdrop) {
                close();
            }
        });
        document.addEventListener('keydown', onKey);
        document.body.classList.add('modal-open');
        App._closeModal = close;
        return modal;
    };

    App.closeModal = function () {
        if (App._closeModal) {
            App._closeModal();
        }
    };

    App.confirm = function (options) {
        return new Promise(function (resolve) {
            var modal = App.openModal({
                title: options.title || '请确认操作',
                body: '<p class="confirm-text">' + App.esc(options.message || '确认执行该操作吗？') + '</p>',
                footer: '<button type="button" class="btn ghost" data-confirm-cancel>取消</button><button type="button" class="btn ' + (options.danger ? 'danger' : 'primary') + '" data-confirm-ok>' + App.esc(options.confirmText || '确认') + '</button>'
            });
            var done = false;

            function finish(value) {
                if (done) {
                    return;
                }
                done = true;
                App.closeModal();
                resolve(value);
            }

            modal.querySelector('[data-confirm-cancel]').addEventListener('click', function () {
                finish(false);
            });
            modal.querySelector('[data-confirm-ok]').addEventListener('click', function () {
                finish(true);
            });
        });
    };

    App.setBusy = function (button, busy, text) {
        if (!button) {
            return;
        }
        if (busy) {
            button.setAttribute('data-label', button.innerHTML);
            button.disabled = true;
            button.textContent = text || '处理中...';
        } else {
            button.disabled = false;
            if (button.getAttribute('data-label')) {
                button.innerHTML = button.getAttribute('data-label');
            }
        }
    };

    App.readForm = function (form) {
        var data = {};
        form.querySelectorAll('[name]').forEach(function (field) {
            var name = field.getAttribute('name');
            if (field.type === 'number') {
                data[name] = field.value === '' ? null : Number(field.value);
            } else {
                data[name] = field.value;
            }
        });
        return data;
    };

    App.optionList = function (items, valueKey, labelKey, selected, placeholder) {
        var html = placeholder === undefined ? '' : '<option value="">' + App.esc(placeholder) + '</option>';
        (items || []).forEach(function (item) {
            var value = typeof valueKey === 'function' ? valueKey(item) : item[valueKey];
            var label = typeof labelKey === 'function' ? labelKey(item) : item[labelKey];
            html += '<option value="' + App.esc(value) + '"' + (String(value) === String(selected) ? ' selected' : '') + '>' + App.esc(label) + '</option>';
        });
        return html;
    };

    App.field = function (label, control, className) {
        return '<div class="field ' + (className || '') + '"><label>' + App.esc(label) + '</label>' + control + '</div>';
    };

    App.roleOptions = function (selected) {
        return App.optionList([
            { value: 'ADMIN', label: '系统管理员' },
            { value: 'AUDITOR', label: '审核人员' },
            { value: 'TAXPAYER', label: '纳税人' }
        ], 'value', 'label', selected, '请选择角色');
    };

    App.statusOptions = function (selected, values, placeholder) {
        return (values || ['正常', '停业', '注销']).map(function (value) {
            return '<option value="' + App.esc(value) + '"' + (value === selected ? ' selected' : '') + '>' + App.esc(value) + '</option>';
        }).join('');
    };

    App.declarationStatusOptions = function (selected, placeholder) {
        return App.optionList([
            { value: '草稿' }, { value: '待审核' }, { value: '已通过' }, { value: '已驳回' }, { value: '已缴款' }
        ], 'value', 'value', selected, placeholder === undefined ? '全部状态' : placeholder);
    };

    App.today = function () {
        return new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' });
    };

    App.go = function (key) {
        if (App.state.route === key && location.hash === '#/' + key) {
            renderRoute();
            return;
        }
        location.hash = '#/' + key;
    };

    App.renderShell = function () {
        var user = App.state.user;
        var navHtml = NAV.map(function (group) {
            var visible = group.items.filter(function (item) {
                return hasRole(item.roles, user.role);
            });
            if (!visible.length) {
                return '';
            }
            return '<div class="nav-group-title">' + App.esc(group.group) + '</div>' + visible.map(function (item) {
                return '<a class="nav-item" href="#/' + item.key + '" data-nav="' + item.key + '">' + App.icon(item.icon) + '<span>' + App.esc(item.title) + '</span></a>';
            }).join('');
        }).join('');

        document.getElementById('app').innerHTML =
            '<aside class="sidebar">' +
            '<div class="sidebar-logo"><div class="mark">税</div><div class="logo-text"><strong>智税通</strong><span>TAX BUSINESS</span></div></div>' +
            '<nav class="sidebar-nav">' + navHtml + '</nav>' +
            '<div class="sidebar-foot">税务业务基础管理系统 · v1.0</div>' +
            '</aside>' +
            '<div class="shell-main">' +
            '<header class="topbar">' +
            '<div class="topbar-left"><span class="crumb" id="page-crumb">业务办理 / 工作台</span><h2 id="page-title">工作台</h2></div>' +
            '<div class="topbar-right">' +
            '<span class="topbar-date">' + App.esc(App.today()) + '</span>' +
            '<div class="user-menu-wrap">' +
            '<button type="button" class="user-chip" data-user-toggle>' +
            '<span class="avatar">' + App.esc((user.displayName || user.username || '?').slice(0, 1)) + '</span>' +
            '<span><span class="u-name">' + App.esc(user.displayName || user.username) + '</span><span class="u-role">' + App.esc(App.roleLabel(user.role)) + '</span></span>' +
            '</button>' +
            '<div class="user-menu hidden" id="user-menu">' +
            '<button type="button" data-action="password">' + App.icon('key', 16) + '修改密码</button>' +
            '<button type="button" class="danger" data-action="logout">' + App.icon('logout', 16) + '安全退出</button>' +
            '</div>' +
            '</div>' +
            '</div>' +
            '</header>' +
            '<main class="view" id="view"></main>' +
            '</div>';

        bindShell();
        renderRoute();
    };

    function bindShell() {
        var menu = document.getElementById('user-menu');
        var toggle = document.querySelector('[data-user-toggle]');

        toggle.addEventListener('click', function (event) {
            event.stopPropagation();
            menu.classList.toggle('hidden');
        });

        document.addEventListener('click', function (event) {
            if (!menu.contains(event.target) && event.target !== toggle) {
                menu.classList.add('hidden');
            }
        });

        menu.querySelector('[data-action="password"]').addEventListener('click', function () {
            menu.classList.add('hidden');
            openPasswordModal();
        });

        menu.querySelector('[data-action="logout"]').addEventListener('click', function () {
            menu.classList.add('hidden');
            App.confirm({
                title: '退出登录',
                message: '确认退出当前账号吗？',
                confirmText: '退出登录'
            }).then(function (ok) {
                if (!ok) {
                    return;
                }
                API.post('/auth/logout').catch(function () {
                    return null;
                }).then(function () {
                    API.clear();
                    window.location.replace('login.html');
                });
            });
        });
    }

    function openPasswordModal() {
        var modal = App.openModal({
            title: '修改登录密码',
            body: '<form id="password-form" class="form-grid">' +
                '<div class="field full"><label>原密码 <span style="color:#d84654">*</span></label><input class="input" type="password" name="oldPassword" autocomplete="current-password" required></div>' +
                '<div class="field full"><label>新密码 <span style="color:#d84654">*</span></label><input class="input" type="password" name="newPassword" autocomplete="new-password" minlength="6" required><div class="form-hint">新密码长度不能少于 6 位</div></div>' +
                '<div class="field full"><label>确认新密码 <span style="color:#d84654">*</span></label><input class="input" type="password" name="confirmPassword" autocomplete="new-password" minlength="6" required></div>' +
                '</form>',
            footer: '<button type="button" class="btn ghost" data-modal-close2>取消</button><button type="button" class="btn primary" id="password-save">保存修改</button>'
        });
        modal.querySelector('[data-modal-close2]').addEventListener('click', App.closeModal);
        modal.querySelector('#password-save').addEventListener('click', function () {
            var form = modal.querySelector('#password-form');
            if (!form.reportValidity()) {
                return;
            }
            var data = App.readForm(form);
            if (data.newPassword !== data.confirmPassword) {
                App.toast('两次输入的新密码不一致', 'error');
                return;
            }
            var button = modal.querySelector('#password-save');
            App.setBusy(button, true, '保存中...');
            API.put('/auth/password', { oldPassword: data.oldPassword, newPassword: data.newPassword }).then(function (result) {
                App.closeModal();
                App.toast((result && result.message) || '密码修改成功');
            }).catch(function (error) {
                App.setBusy(button, false);
                App.toast(error.message, 'error');
            });
        });
    }

    function currentKey() {
        return (location.hash.replace(/^#\/?/, '').split('?')[0] || 'dashboard');
    }

    function renderRoute() {
        if (!App.state.user) {
            return;
        }
        var key = currentKey();
        var page = App.pages[key];
        if (!page) {
            key = 'dashboard';
            page = App.pages.dashboard;
        }
        if (!page) {
            return;
        }
        if (!hasRole(page.roles, App.state.user.role)) {
            App.toast('当前账号无权访问该页面', 'error');
            location.hash = '#/dashboard';
            return;
        }
        var item = navItem(key);
        App.state.route = key;
        document.querySelectorAll('.nav-item').forEach(function (link) {
            link.classList.toggle('active', link.getAttribute('data-nav') === key);
        });
        document.getElementById('page-title').textContent = page.title || (item ? item.title : '工作台');
        document.getElementById('page-crumb').textContent = page.crumb || ('业务办理 / ' + (item ? item.title : '工作台'));
        var view = document.getElementById('view');
        view.innerHTML = App.loading();
        try {
            var promise = page.render(view, App.state.user);
            if (promise && promise.catch) {
                promise.catch(function (error) {
                    App.showError(view, error);
                });
            }
        } catch (error) {
            App.showError(view, error);
        }
    }

    App.start = function () {
        if (!API.token()) {
            window.location.replace('login.html');
            return;
        }
        document.getElementById('app').innerHTML = '<div class="loading" style="min-height:70vh"><div class="spinner"></div><span>正在进入税务工作台...</span></div>';
        API.get('/auth/me').then(function (user) {
            API.setUser(user);
            App.state.user = user;
            App.renderShell();
            window.addEventListener('hashchange', renderRoute);
        }).catch(function (error) {
            if (!API.token()) {
                return;
            }
            document.getElementById('app').innerHTML = '<div class="loading" style="min-height:70vh"><div class="empty-icon">' + App.icon('info', 26) + '</div><strong>系统初始化失败</strong><p class="muted">' + App.esc(error.message || '请稍后刷新页面重试') + '</p><button class="btn ghost" onclick="location.reload()">重新加载</button></div>';
        });
    };

    App.renderRoute = renderRoute;
    App.currentRoute = currentKey;
    document.addEventListener('DOMContentLoaded', function () {
        App.start();
    });
})();
