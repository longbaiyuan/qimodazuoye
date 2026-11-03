(function () {
    'use strict';

    var App = window.App;
    var taxpayerCache = null;

    function summaryCard(icon, color, label, value, hint) {
        return '<div class="stat-card"><div class="stat-icon ' + color + '">' + App.icon(icon, 19) + '</div><div class="stat-body"><div class="label">' + App.esc(label) + '</div><div class="value">' + value + '</div>' + (hint ? '<div class="hint">' + App.esc(hint) + '</div>' : '') + '</div></div>';
    }

    function loadTaxpayerOptions() {
        if (!taxpayerCache) {
            taxpayerCache = API.get('/taxpayers/options');
        }
        return taxpayerCache;
    }

    function downloadCsv(filename, rows) {
        var content = '\uFEFF' + rows.map(function (row) {
            return row.map(function (cell) {
                var text = cell === null || cell === undefined ? '' : String(cell);
                return '"' + text.replace(/"/g, '""') + '"';
            }).join(',');
        }).join('\r\n');
        var blob = new Blob([content], { type: 'text/csv;charset=utf-8' });
        var url = URL.createObjectURL(blob);
        var link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.setTimeout(function () {
            URL.revokeObjectURL(url);
        }, 1000);
    }

    App.pages.query = {
        title: '综合查询',
        crumb: '综合应用 / 综合查询',
        render: function (view, user) {
            var state = {
                keyword: '',
                status: '',
                taxTypeId: '',
                taxpayerId: '',
                startDate: '',
                endDate: '',
                minAmount: '',
                maxAmount: '',
                page: 1,
                size: 10,
                items: []
            };
            var isTaxpayer = user.role === 'TAXPAYER';

            view.innerHTML =
                '<div class="page-head"><div><h3>综合查询</h3><p>按申报状态、税种、所属期和金额区间组合查询，支持导出当前筛选结果。</p></div>' +
                '<div class="page-actions"><button type="button" class="btn ghost" id="query-export">' + App.icon('download', 15) + '导出 CSV</button></div></div>' +
                '<div class="stat-grid four" id="query-summary"></div>' +
                '<div id="query-status-chips" class="status-chips"></div>' +
                '<div class="card filter-bar">' +
                '<div class="field grow"><label>关键词</label><input class="input" id="query-keyword" placeholder="申报编号 / 纳税人 / 识别号"></div>' +
                '<div class="field"><label>申报状态</label><select class="select" id="query-status">' + App.declarationStatusOptions('', '全部状态') + '</select></div>' +
                '<div class="field"><label>税种</label><select class="select" id="query-tax-type"><option value="">全部税种</option></select></div>' +
                (isTaxpayer ? '' : '<div class="field"><label>纳税人</label><select class="select" id="query-taxpayer"><option value="">全部纳税人</option></select></div>') +
                '<div class="field"><label>所属期起</label><input class="input" type="date" id="query-start"></div>' +
                '<div class="field"><label>所属期止</label><input class="input" type="date" id="query-end"></div>' +
                '<div class="field"><label>最低税额</label><input class="input" type="number" min="0" step="0.01" id="query-min" placeholder="0.00"></div>' +
                '<div class="field"><label>最高税额</label><input class="input" type="number" min="0" step="0.01" id="query-max" placeholder="不限"></div>' +
                '<div class="filter-actions"><button type="button" class="btn primary" id="query-search">' + App.icon('query', 15) + '查询</button><button type="button" class="btn ghost" id="query-reset">' + App.icon('refresh', 15) + '重置</button></div>' +
                '</div>' +
                '<div class="card"><div class="table-wrap" id="query-table">' + App.loading() + '</div><div id="query-pager"></div></div>';

            var table = view.querySelector('#query-table');
            var pager = view.querySelector('#query-pager');
            var keyword = view.querySelector('#query-keyword');
            var status = view.querySelector('#query-status');
            var taxType = view.querySelector('#query-tax-type');
            var taxpayer = view.querySelector('#query-taxpayer');
            var start = view.querySelector('#query-start');
            var end = view.querySelector('#query-end');
            var min = view.querySelector('#query-min');
            var max = view.querySelector('#query-max');
            var exportButton = view.querySelector('#query-export');

            API.get('/tax-types/options').then(function (items) {
                taxType.innerHTML = '<option value="">全部税种</option>' + App.optionList(items, 'id', function (item) {
                    return item.name + ' · ' + App.rate(item.rate);
                }, '');
            }).catch(function () {
                return null;
            });
            if (taxpayer) {
                loadTaxpayerOptions().then(function (items) {
                    taxpayer.innerHTML = '<option value="">全部纳税人</option>' + App.optionList(items, 'id', function (item) {
                        return item.name;
                    }, '');
                }).catch(function () {
                    return null;
                });
            }

            function params() {
                return {
                    keyword: state.keyword,
                    status: state.status,
                    taxTypeId: state.taxTypeId,
                    taxpayerId: state.taxpayerId,
                    startDate: state.startDate,
                    endDate: state.endDate,
                    minAmount: state.minAmount,
                    maxAmount: state.maxAmount
                };
            }

            function load(page) {
                state.page = page || state.page;
                table.innerHTML = App.loading();
                pager.innerHTML = '';
                var summaryRequest = API.get('/query/summary' + App.query(params())).catch(function () {
                    return null;
                });
                var listRequest = API.get('/query/declarations' + App.query(Object.assign(params(), {
                    page: state.page,
                    size: state.size
                })));
                Promise.all([listRequest, summaryRequest]).then(function (results) {
                    var result = results[0];
                    var summary = results[1];
                    state.items = result.items || [];
                    renderSummary(summary);
                    if (!state.items.length) {
                        table.innerHTML = App.empty('没有符合条件的申报', '请调整查询条件后重试');
                        return;
                    }
                    table.innerHTML = '<table class="data"><thead><tr><th>申报编号</th><th>纳税人</th><th>税种</th><th>所属期</th><th>计税金额</th><th>税率</th><th>应纳税额</th><th>已缴税额</th><th>状态</th><th>操作</th></tr></thead><tbody>' +
                        state.items.map(function (item) {
                            return '<tr><td><div class="cell-main mono">' + App.esc(item.declarationNo) + '</div><div class="cell-sub">' + App.esc(App.dateTime(item.createdAt)) + '</div></td>' +
                                '<td><div class="cell-main">' + App.esc(item.taxpayerName) + '</div><div class="cell-sub mono">' + App.esc(item.taxpayerNo) + '</div></td>' +
                                '<td><div class="cell-main" style="font-weight:500">' + App.esc(item.taxTypeName) + '</div><div class="cell-sub">' + App.esc(item.taxTypeCode) + '</div></td>' +
                                '<td><span class="cell-date">' + App.esc(App.dateOnly(item.periodStart)) + ' 至 ' + App.esc(App.dateOnly(item.periodEnd)) + '</span></td>' +
                                '<td><span class="cell-money">' + App.money(item.taxableAmount) + '</span></td>' +
                                '<td><span class="cell-money">' + App.rate(item.taxRate) + '</span></td>' +
                                '<td><span class="cell-money">' + App.money(item.taxAmount) + '</span></td>' +
                                '<td><span class="cell-money">' + App.money(item.paidAmount) + '</span></td>' +
                                '<td>' + App.declarationBadge(item.status) + '</td>' +
                                '<td class="actions"><button type="button" class="btn link" data-view="' + item.id + '">详情</button></td></tr>';
                        }).join('') + '</tbody></table>';
                    pager.innerHTML = App.pager(result);
                    App.bindPager(view, result, load);
                    table.querySelectorAll('[data-view]').forEach(function (button) {
                        button.addEventListener('click', function () {
                            if (App.actions && App.actions.showDeclaration) {
                                App.actions.showDeclaration(Number(button.getAttribute('data-view')));
                            }
                        });
                    });
                }).catch(function (error) {
                    table.innerHTML = App.errorBox(error);
                });
            }

            function renderSummary(summary) {
                var box = view.querySelector('#query-summary');
                var chips = view.querySelector('#query-status-chips');
                if (!summary) {
                    box.innerHTML = '';
                    chips.innerHTML = '';
                    return;
                }
                box.innerHTML =
                    summaryCard('declarations', 'blue', '申报笔数', App.num(summary.count, 0), '符合当前条件') +
                    summaryCard('card', 'navy', '计税金额', App.money(summary.taxableTotal), '计税依据合计') +
                    summaryCard('tax', 'orange', '应纳税额', App.money(summary.taxTotal), '应征税额合计') +
                    summaryCard('payment', 'green', '已缴税额', App.money(summary.paidTotal), '已入库税额');
                chips.innerHTML = (summary.statusStats || []).map(function (item) {
                    return '<span class="status-chip">' + App.declarationBadge(item.status) + '<strong>' + App.num(item.count, 0) + '</strong></span>';
                }).join('');
            }

            function applyFilters() {
                state.keyword = keyword.value.trim();
                state.status = status.value;
                state.taxTypeId = taxType.value;
                state.taxpayerId = taxpayer ? taxpayer.value : '';
                state.startDate = start.value;
                state.endDate = end.value;
                state.minAmount = min.value;
                state.maxAmount = max.value;
            }

            function exportCsv() {
                applyFilters();
                if (state.minAmount !== '' && state.maxAmount !== '' && Number(state.minAmount) > Number(state.maxAmount)) {
                    App.toast('最低税额不能大于最高税额', 'error');
                    return;
                }
                App.setBusy(exportButton, true, '导出中...');
                var all = [];
                var page = 1;
                var total = 1;
                function fetchPage() {
                    return API.get('/query/declarations' + App.query(Object.assign(params(), {
                        page: page,
                        size: 200
                    }))).then(function (result) {
                        total = Number(result.total || 0);
                        all = all.concat(result.items || []);
                        if (all.length < total) {
                            page++;
                            return fetchPage();
                        }
                        var rows = [['申报编号', '纳税人', '纳税人识别号', '税种', '所属期起', '所属期止', '计税金额', '税率(%)', '应纳税额', '已缴税额', '状态', '提交时间', '审核人', '审核时间', '审核意见']];
                        all.forEach(function (item) {
                            rows.push([
                                item.declarationNo, item.taxpayerName, item.taxpayerNo, item.taxTypeName,
                                App.dateOnly(item.periodStart), App.dateOnly(item.periodEnd),
                                item.taxableAmount, item.taxRate, item.taxAmount, item.paidAmount,
                                item.status, App.dateTime(item.submitTime), item.reviewer || '',
                                App.dateTime(item.reviewTime), item.reviewComment || ''
                            ]);
                        });
                        downloadCsv('纳税申报查询_' + new Date().toISOString().slice(0, 10) + '.csv', rows);
                        App.toast('已导出 ' + all.length + ' 条申报记录');
                    });
                }
                fetchPage().catch(function (error) {
                    App.toast(error.message, 'error');
                }).then(function () {
                    App.setBusy(exportButton, false);
                });
            }

            view.querySelector('#query-search').addEventListener('click', function () {
                applyFilters();
                if (state.minAmount !== '' && state.maxAmount !== '' && Number(state.minAmount) > Number(state.maxAmount)) {
                    App.toast('最低税额不能大于最高税额', 'error');
                    return;
                }
                load(1);
            });
            view.querySelector('#query-reset').addEventListener('click', function () {
                keyword.value = '';
                status.value = '';
                taxType.value = '';
                if (taxpayer) {
                    taxpayer.value = '';
                }
                start.value = '';
                end.value = '';
                min.value = '';
                max.value = '';
                state.keyword = '';
                state.status = '';
                state.taxTypeId = '';
                state.taxpayerId = '';
                state.startDate = '';
                state.endDate = '';
                state.minAmount = '';
                state.maxAmount = '';
                load(1);
            });
            exportButton.addEventListener('click', exportCsv);
            keyword.addEventListener('keydown', function (event) {
                if (event.key === 'Enter') {
                    view.querySelector('#query-search').click();
                }
            });
            load(1);
        }
    };

    App.pages.users = {
        title: '用户管理',
        crumb: '基础管理 / 用户管理',
        roles: ['ADMIN'],
        render: function (view, user) {
            var state = { keyword: '', role: '', page: 1, size: 10, items: [] };

            view.innerHTML =
                '<div class="page-head"><div><h3>用户管理</h3><p>维护系统账号、角色权限及纳税人绑定关系。</p></div>' +
                '<div class="page-actions"><button type="button" class="btn primary" id="user-create">' + App.icon('plus', 15) + '新增用户</button></div></div>' +
                '<div class="card filter-bar"><div class="field grow"><label>关键词</label><input class="input" id="user-keyword" placeholder="登录账号 / 姓名"></div>' +
                '<div class="field"><label>用户角色</label><select class="select" id="user-role">' + App.roleOptions('') + '</select></div>' +
                '<div class="filter-actions"><button type="button" class="btn primary" id="user-search">' + App.icon('query', 15) + '查询</button><button type="button" class="btn ghost" id="user-reset">' + App.icon('refresh', 15) + '重置</button></div></div>' +
                '<div class="card"><div class="table-wrap" id="user-table">' + App.loading() + '</div><div id="user-pager"></div></div>';

            var table = view.querySelector('#user-table');
            var pager = view.querySelector('#user-pager');
            var keyword = view.querySelector('#user-keyword');
            var role = view.querySelector('#user-role');

            function load(page) {
                state.page = page || state.page;
                table.innerHTML = App.loading();
                pager.innerHTML = '';
                API.get('/users' + App.query({
                    keyword: state.keyword,
                    role: state.role,
                    page: state.page,
                    size: state.size
                })).then(function (result) {
                    state.items = result.items || [];
                    if (!state.items.length) {
                        table.innerHTML = App.empty('暂无用户', '可以调整筛选条件或新增系统账号');
                        return;
                    }
                    table.innerHTML = '<table class="data"><thead><tr><th>登录账号</th><th>用户姓名</th><th>角色</th><th>绑定纳税人</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead><tbody>' +
                        state.items.map(function (item) {
                            return '<tr><td><div class="cell-main mono">' + App.esc(item.username) + '</div></td>' +
                                '<td><span class="cell-main">' + App.esc(item.displayName) + '</span></td>' +
                                '<td>' + App.roleBadge(item.role) + '</td>' +
                                '<td>' + App.esc(item.taxpayerName || '—') + '</td>' +
                                '<td>' + App.userStatusBadge(item.status) + '</td>' +
                                '<td><span class="cell-date">' + App.esc(App.dateTime(item.createdAt)) + '</span></td>' +
                                '<td class="actions"><button type="button" class="btn link" data-edit="' + item.id + '">编辑</button>' +
                                (item.id !== user.id ? '<button type="button" class="btn link danger" data-delete="' + item.id + '">删除</button>' : '') + '</td></tr>';
                        }).join('') + '</tbody></table>';
                    pager.innerHTML = App.pager(result);
                    App.bindPager(view, result, load);
                    table.querySelectorAll('[data-edit]').forEach(function (button) {
                        button.addEventListener('click', function () {
                            openForm(findItem(button.getAttribute('data-edit')));
                        });
                    });
                    table.querySelectorAll('[data-delete]').forEach(function (button) {
                        button.addEventListener('click', function () {
                            remove(findItem(button.getAttribute('data-delete')));
                        });
                    });
                }).catch(function (error) {
                    table.innerHTML = App.errorBox(error);
                });
            }

            function findItem(id) {
                return state.items.filter(function (item) {
                    return item.id === Number(id);
                })[0];
            }

            function openForm(item) {
                var editing = !!item;
                var data = item || {};
                Promise.all([loadTaxpayerOptions()]).then(function (results) {
                    var taxpayers = results[0] || [];
                    var modal = App.openModal({
                        title: editing ? '编辑用户' : '新增用户',
                        wide: true,
                        body: '<form id="user-form" class="form-grid">' +
                            '<div class="field"><label>登录账号 ' + (editing ? '' : '<span style="color:#d84654">*</span>') + '</label><input class="input mono" name="username" maxlength="40" value="' + App.esc(data.username) + '"' + (editing ? ' disabled' : ' required') + '>' +
                            (editing ? '<div class="form-hint">登录账号创建后不可修改</div>' : '') + '</div>' +
                            '<div class="field"><label>用户姓名 <span style="color:#d84654">*</span></label><input class="input" name="displayName" maxlength="40" value="' + App.esc(data.displayName) + '" required></div>' +
                            '<div class="field"><label>用户角色 <span style="color:#d84654">*</span></label><select class="select" name="role" id="user-form-role" required>' + App.roleOptions(data.role || 'AUDITOR') + '</select></div>' +
                            '<div class="field"><label>账号状态 <span style="color:#d84654">*</span></label><select class="select" name="status" required>' + App.statusOptions(data.status || '启用', ['启用', '停用']) + '</select></div>' +
                            '<div class="field full" id="user-taxpayer-wrap"><label>绑定纳税人 <span style="color:#d84654">*</span></label><select class="select" name="taxpayerId" id="user-taxpayer">' +
                            App.optionList(taxpayers, 'id', function (taxpayer) {
                                return taxpayer.name + (taxpayer.taxpayerNo ? '（' + taxpayer.taxpayerNo + '）' : '');
                            }, data.taxpayerId, '请选择纳税人') + '</select><div class="form-hint">纳税人账号只能绑定一个正常状态的纳税人主体</div></div>' +
                            '<div class="field full"><label>' + (editing ? '重置密码（留空则不修改）' : '登录密码') + (editing ? '' : ' <span style="color:#d84654">*</span>') + '</label><input class="input" type="password" name="password" minlength="6" autocomplete="new-password" ' + (editing ? '' : 'required') + '><div class="form-hint">密码长度不能少于 6 位</div></div>' +
                            '</form>',
                        footer: '<button type="button" class="btn ghost" data-form-cancel>取消</button><button type="button" class="btn primary" id="user-save">' + (editing ? '保存修改' : '确认新增') + '</button>'
                    });
                    var form = modal.querySelector('#user-form');
                    var roleSelect = modal.querySelector('#user-form-role');
                    var taxpayerWrap = modal.querySelector('#user-taxpayer-wrap');
                    var taxpayerSelect = modal.querySelector('#user-taxpayer');

                    function toggleTaxpayer() {
                        var need = roleSelect.value === 'TAXPAYER';
                        taxpayerWrap.classList.toggle('hidden', !need);
                        taxpayerSelect.required = need;
                    }

                    roleSelect.addEventListener('change', toggleTaxpayer);
                    toggleTaxpayer();
                    modal.querySelector('[data-form-cancel]').addEventListener('click', App.closeModal);
                    modal.querySelector('#user-save').addEventListener('click', function () {
                        if (!form.reportValidity()) {
                            return;
                        }
                        var raw = App.readForm(form);
                        var payload = {
                            displayName: raw.displayName,
                            role: raw.role,
                            status: raw.status,
                            taxpayerId: raw.role === 'TAXPAYER' && raw.taxpayerId ? Number(raw.taxpayerId) : null
                        };
                        if (raw.password) {
                            payload.password = raw.password;
                        }
                        if (!editing) {
                            payload.username = raw.username;
                            payload.password = raw.password;
                        }
                        if (payload.role === 'TAXPAYER' && !payload.taxpayerId) {
                            App.toast('请选择纳税人账号绑定的纳税人', 'error');
                            return;
                        }
                        var button = modal.querySelector('#user-save');
                        App.setBusy(button, true, '保存中...');
                        var request = editing ? API.put('/users/' + item.id, payload) : API.post('/users', payload);
                        request.then(function () {
                            App.closeModal();
                            App.toast(editing ? '用户信息已更新' : '用户已创建');
                            load(editing ? state.page : 1);
                        }).catch(function (error) {
                            App.setBusy(button, false);
                            App.toast(error.message, 'error');
                        });
                    });
                }).catch(function (error) {
                    App.toast(error.message, 'error');
                });
            }

            function remove(item) {
                App.confirm({
                    title: '删除用户',
                    message: '确认删除账号“' + item.username + '”吗？删除后该账号将无法登录系统。',
                    confirmText: '确认删除',
                    danger: true
                }).then(function (ok) {
                    if (!ok) {
                        return;
                    }
                    API.del('/users/' + item.id).then(function (result) {
                        App.toast((result && result.message) || '用户已删除');
                        load();
                    }).catch(function (error) {
                        App.toast(error.message, 'error');
                    });
                });
            }

            view.querySelector('#user-create').addEventListener('click', function () {
                openForm(null);
            });
            view.querySelector('#user-search').addEventListener('click', function () {
                state.keyword = keyword.value.trim();
                state.role = role.value;
                load(1);
            });
            view.querySelector('#user-reset').addEventListener('click', function () {
                keyword.value = '';
                role.value = '';
                state.keyword = '';
                state.role = '';
                load(1);
            });
            keyword.addEventListener('keydown', function (event) {
                if (event.key === 'Enter') {
                    view.querySelector('#user-search').click();
                }
            });
            load(1);
        }
    };

    App.pages.logs = {
        title: '操作日志',
        crumb: '综合应用 / 操作日志',
        roles: ['ADMIN'],
        render: function (view) {
            var state = { keyword: '', module: '', page: 1, size: 15, items: [] };
            var modules = ['认证', '申报', '审核', '纳税人', '税种', '发票', '缴款', '用户', '系统'];

            view.innerHTML =
                '<div class="page-head"><div><h3>操作日志</h3><p>查看系统关键业务操作记录，便于追溯与审计。</p></div></div>' +
                '<div class="card filter-bar"><div class="field grow"><label>关键词</label><input class="input" id="log-keyword" placeholder="账号 / 操作 / 详情"></div>' +
                '<div class="field"><label>业务模块</label><select class="select" id="log-module">' + App.optionList(modules.map(function (item) { return { value: item }; }), 'value', 'value', '', '全部模块') + '</select></div>' +
                '<div class="filter-actions"><button type="button" class="btn primary" id="log-search">' + App.icon('query', 15) + '查询</button><button type="button" class="btn ghost" id="log-reset">' + App.icon('refresh', 15) + '重置</button></div></div>' +
                '<div class="card"><div class="table-wrap" id="log-table">' + App.loading() + '</div><div id="log-pager"></div></div>';

            var table = view.querySelector('#log-table');
            var pager = view.querySelector('#log-pager');
            var keyword = view.querySelector('#log-keyword');
            var module = view.querySelector('#log-module');

            function load(page) {
                state.page = page || state.page;
                table.innerHTML = App.loading();
                pager.innerHTML = '';
                API.get('/logs' + App.query({
                    keyword: state.keyword,
                    module: state.module,
                    page: state.page,
                    size: state.size
                })).then(function (result) {
                    state.items = result.items || [];
                    if (!state.items.length) {
                        table.innerHTML = App.empty('暂无操作日志', '系统产生业务操作后会记录在这里');
                        return;
                    }
                    table.innerHTML = '<table class="data"><thead><tr><th>操作时间</th><th>操作账号</th><th>模块</th><th>动作</th><th>操作详情</th></tr></thead><tbody>' +
                        state.items.map(function (item) {
                            return '<tr><td><span class="cell-date">' + App.esc(App.dateTime(item.createdAt)) + '</span></td>' +
                                '<td><span class="cell-main mono">' + App.esc(item.username) + '</span></td>' +
                                '<td><span class="badge blue">' + App.esc(item.module) + '</span></td>' +
                                '<td><span class="cell-main" style="font-weight:600">' + App.esc(item.action) + '</span></td>' +
                                '<td class="muted">' + App.esc(item.detail || '—') + '</td></tr>';
                        }).join('') + '</tbody></table>';
                    pager.innerHTML = App.pager(result);
                    App.bindPager(view, result, load);
                }).catch(function (error) {
                    table.innerHTML = App.errorBox(error);
                });
            }

            view.querySelector('#log-search').addEventListener('click', function () {
                state.keyword = keyword.value.trim();
                state.module = module.value;
                load(1);
            });
            view.querySelector('#log-reset').addEventListener('click', function () {
                keyword.value = '';
                module.value = '';
                state.keyword = '';
                state.module = '';
                load(1);
            });
            keyword.addEventListener('keydown', function (event) {
                if (event.key === 'Enter') {
                    view.querySelector('#log-search').click();
                }
            });
            load(1);
        }
    };
})();
