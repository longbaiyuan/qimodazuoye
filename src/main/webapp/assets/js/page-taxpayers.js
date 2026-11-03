(function () {
    'use strict';

    var App = window.App;

    function taxpayerTypes(selected) {
        var options = ['企业', '个体工商户', '自然人', '其他'];
        return options.map(function (type) {
            return '<option value="' + type + '"' + (type === selected ? ' selected' : '') + '>' + type + '</option>';
        }).join('');
    }

    function formHtml(item) {
        var data = item || {};
        return '<form id="taxpayer-form" class="form-grid">' +
            '<div class="field"><label>纳税人识别号 <span style="color:#d84654">*</span></label><input class="input mono" name="taxpayerNo" maxlength="30" value="' + App.esc(data.taxpayerNo) + '" placeholder="如 91310000MA1K12345A" required></div>' +
            '<div class="field"><label>纳税人名称 <span style="color:#d84654">*</span></label><input class="input" name="name" maxlength="80" value="' + App.esc(data.name) + '" placeholder="请输入单位或个人信息" required></div>' +
            '<div class="field"><label>纳税人类型 <span style="color:#d84654">*</span></label><select class="select" name="taxpayerType" required>' + taxpayerTypes(data.taxpayerType || '企业') + '</select></div>' +
            '<div class="field"><label>法定代表人</label><input class="input" name="legalPerson" maxlength="30" value="' + App.esc(data.legalPerson) + '" placeholder="请输入姓名"></div>' +
            '<div class="field"><label>身份证号 / 证件号码</label><input class="input mono" name="idCard" maxlength="30" value="' + App.esc(data.idCard) + '"></div>' +
            '<div class="field"><label>联系电话</label><input class="input" name="phone" maxlength="20" value="' + App.esc(data.phone) + '"></div>' +
            '<div class="field"><label>电子邮箱</label><input class="input" type="email" name="email" maxlength="80" value="' + App.esc(data.email) + '"></div>' +
            '<div class="field"><label>所属行业</label><input class="input" name="industry" maxlength="40" value="' + App.esc(data.industry) + '"></div>' +
            '<div class="field"><label>登记日期</label><input class="input" type="date" name="registrationDate" value="' + App.esc(data.registrationDate) + '"></div>' +
            '<div class="field"><label>主管税务机关</label><input class="input" name="taxOffice" maxlength="80" value="' + App.esc(data.taxOffice) + '"></div>' +
            '<div class="field"><label>登记状态 <span style="color:#d84654">*</span></label><select class="select" name="status" required>' + App.statusOptions(data.status || '正常', ['正常', '停业', '注销']) + '</select></div>' +
            '<div class="field full"><label>经营地址</label><input class="input" name="address" maxlength="160" value="' + App.esc(data.address) + '"></div>' +
            '</form>';
    }

    function detailItem(label, value, full) {
        return '<div class="detail-item' + (full ? ' full' : '') + '"><label>' + App.esc(label) + '</label><span>' + App.esc(value || '—') + '</span></div>';
    }

    App.pages.taxpayers = {
        title: '纳税人管理',
        crumb: '基础管理 / 纳税人管理',
        roles: ['ADMIN', 'AUDITOR'],
        render: function (view, user) {
            var state = { keyword: '', status: '', page: 1, size: 10, items: [] };
            var canDelete = user.role === 'ADMIN';

            view.innerHTML =
                '<div class="page-head"><div><h3>纳税人管理</h3><p>维护纳税人登记档案，支持按名称、识别号、法人和电话检索。</p></div>' +
                '<div class="page-actions"><button type="button" class="btn primary" id="taxpayer-create">' + App.icon('plus', 15) + '新增纳税人</button></div></div>' +
                '<div class="card filter-bar">' +
                '<div class="field grow"><label>关键词</label><input class="input" id="taxpayer-keyword" placeholder="名称 / 识别号 / 法人 / 电话"></div>' +
                '<div class="field"><label>登记状态</label><select class="select" id="taxpayer-status">' + App.optionList([{ v: '正常' }, { v: '停业' }, { v: '注销' }], 'v', 'v', '', '全部状态') + '</select></div>' +
                '<div class="filter-actions"><button type="button" class="btn primary" id="taxpayer-search">' + App.icon('query', 15) + '查询</button><button type="button" class="btn ghost" id="taxpayer-reset">' + App.icon('refresh', 15) + '重置</button></div>' +
                '</div>' +
                '<div class="card"><div class="table-wrap" id="taxpayer-table">' + App.loading() + '</div><div id="taxpayer-pager"></div></div>';

            var table = view.querySelector('#taxpayer-table');
            var pager = view.querySelector('#taxpayer-pager');
            var keyword = view.querySelector('#taxpayer-keyword');
            var status = view.querySelector('#taxpayer-status');

            function load(page) {
                state.page = page || state.page;
                table.innerHTML = App.loading();
                pager.innerHTML = '';
                API.get('/taxpayers' + App.query({
                    keyword: state.keyword,
                    status: state.status,
                    page: state.page,
                    size: state.size
                })).then(function (result) {
                    state.items = result.items || [];
                    if (!state.items.length) {
                        table.innerHTML = App.empty('暂无纳税人', '可以调整筛选条件或新增纳税人档案');
                        return;
                    }
                    table.innerHTML = '<table class="data"><thead><tr><th>纳税人</th><th>类型</th><th>法人 / 电话</th><th>主管税务机关</th><th>状态</th><th>登记日期</th><th>操作</th></tr></thead><tbody>' +
                        state.items.map(function (item) {
                            return '<tr><td><div class="cell-main">' + App.esc(item.name) + '</div><div class="cell-sub mono">' + App.esc(item.taxpayerNo) + '</div></td>' +
                                '<td>' + App.esc(item.taxpayerType || '—') + '</td>' +
                                '<td><div class="cell-main">' + App.esc(item.legalPerson || '—') + '</div><div class="cell-sub">' + App.esc(item.phone || '—') + '</div></td>' +
                                '<td><div class="cell-main" style="font-weight:500">' + App.esc(item.taxOffice || '—') + '</div><div class="cell-sub">' + App.esc(item.industry || '—') + '</div></td>' +
                                '<td>' + App.taxpayerBadge(item.status) + '</td>' +
                                '<td><span class="cell-date">' + App.esc(App.dateOnly(item.registrationDate)) + '</span></td>' +
                                '<td class="actions"><button type="button" class="btn link" data-view="' + item.id + '">查看</button><button type="button" class="btn link" data-edit="' + item.id + '">编辑</button>' +
                                (canDelete ? '<button type="button" class="btn link danger" data-delete="' + item.id + '">删除</button>' : '') + '</td></tr>';
                        }).join('') + '</tbody></table>';
                    pager.innerHTML = App.pager(result);
                    App.bindPager(view, result, load);
                    table.querySelectorAll('[data-view]').forEach(function (button) {
                        button.addEventListener('click', function () {
                            showDetail(Number(button.getAttribute('data-view')));
                        });
                    });
                    table.querySelectorAll('[data-edit]').forEach(function (button) {
                        button.addEventListener('click', function () {
                            var item = state.items.filter(function (row) {
                                return row.id === Number(button.getAttribute('data-edit'));
                            })[0];
                            openForm(item);
                        });
                    });
                    table.querySelectorAll('[data-delete]').forEach(function (button) {
                        button.addEventListener('click', function () {
                            var item = state.items.filter(function (row) {
                                return row.id === Number(button.getAttribute('data-delete'));
                            })[0];
                            remove(item);
                        });
                    });
                }).catch(function (error) {
                    table.innerHTML = App.errorBox(error);
                });
            }

            function showDetail(id) {
                API.get('/taxpayers/' + id).then(function (item) {
                    var modal = App.openModal({
                        title: '纳税人档案 · ' + item.name,
                        wide: true,
                        body: '<div class="detail-section-title">登记信息</div><div class="detail-grid">' +
                            detailItem('纳税人识别号', item.taxpayerNo) + detailItem('纳税人名称', item.name) +
                            detailItem('纳税人类型', item.taxpayerType) + detailItem('登记状态', item.status) +
                            detailItem('法定代表人', item.legalPerson) + detailItem('身份证号 / 证件号码', item.idCard) +
                            detailItem('联系电话', item.phone) + detailItem('电子邮箱', item.email) +
                            detailItem('所属行业', item.industry) + detailItem('登记日期', App.dateOnly(item.registrationDate)) +
                            detailItem('主管税务机关', item.taxOffice, true) + detailItem('经营地址', item.address, true) +
                            '</div><div class="detail-section-title">档案时间</div><div class="detail-grid">' +
                            detailItem('创建时间', App.dateTime(item.createdAt)) + detailItem('更新时间', App.dateTime(item.updatedAt)) + '</div>',
                        footer: '<button type="button" class="btn ghost" data-detail-close>关闭</button><button type="button" class="btn primary" data-detail-edit>编辑档案</button>'
                    });
                    modal.querySelector('[data-detail-close]').addEventListener('click', App.closeModal);
                    modal.querySelector('[data-detail-edit]').addEventListener('click', function () {
                        App.closeModal();
                        openForm(item);
                    });
                }).catch(function (error) {
                    App.toast(error.message, 'error');
                });
            }

            function openForm(item) {
                var editing = !!item;
                var modal = App.openModal({
                    title: editing ? '编辑纳税人' : '新增纳税人',
                    wide: true,
                    body: formHtml(item),
                    footer: '<button type="button" class="btn ghost" data-form-cancel>取消</button><button type="button" class="btn primary" id="taxpayer-save">' + (editing ? '保存修改' : '确认新增') + '</button>'
                });
                var form = modal.querySelector('#taxpayer-form');
                modal.querySelector('[data-form-cancel]').addEventListener('click', App.closeModal);
                modal.querySelector('#taxpayer-save').addEventListener('click', function () {
                    if (!form.reportValidity()) {
                        return;
                    }
                    var button = modal.querySelector('#taxpayer-save');
                    var payload = App.readForm(form);
                    App.setBusy(button, true, '保存中...');
                    var request = editing ? API.put('/taxpayers/' + item.id, payload) : API.post('/taxpayers', payload);
                    request.then(function () {
                        App.closeModal();
                        App.toast(editing ? '纳税人档案已更新' : '纳税人档案已创建');
                        load(editing ? state.page : 1);
                    }).catch(function (error) {
                        App.setBusy(button, false);
                        App.toast(error.message, 'error');
                    });
                });
            }

            function remove(item) {
                App.confirm({
                    title: '删除纳税人',
                    message: '确认删除“' + item.name + '”的纳税人档案吗？存在申报、发票或账号绑定记录时无法删除。',
                    confirmText: '确认删除',
                    danger: true
                }).then(function (ok) {
                    if (!ok) {
                        return;
                    }
                    API.del('/taxpayers/' + item.id).then(function (result) {
                        App.toast((result && result.message) || '纳税人已删除');
                        load();
                    }).catch(function (error) {
                        App.toast(error.message, 'error');
                    });
                });
            }

            view.querySelector('#taxpayer-create').addEventListener('click', function () {
                openForm(null);
            });
            view.querySelector('#taxpayer-search').addEventListener('click', function () {
                state.keyword = keyword.value.trim();
                state.status = status.value;
                load(1);
            });
            view.querySelector('#taxpayer-reset').addEventListener('click', function () {
                keyword.value = '';
                status.value = '';
                state.keyword = '';
                state.status = '';
                load(1);
            });
            keyword.addEventListener('keydown', function (event) {
                if (event.key === 'Enter') {
                    view.querySelector('#taxpayer-search').click();
                }
            });
            load(1);
        }
    };

    App.pages['tax-types'] = {
        title: '税种管理',
        crumb: '基础管理 / 税种管理',
        roles: ['ADMIN'],
        render: function (view) {
            var state = { keyword: '', status: '', page: 1, size: 10, items: [] };

            view.innerHTML =
                '<div class="page-head"><div><h3>税种管理</h3><p>维护税种代码、名称、税率及启用状态，税率以百分比计算。</p></div>' +
                '<div class="page-actions"><button type="button" class="btn primary" id="tax-type-create">' + App.icon('plus', 15) + '新增税种</button></div></div>' +
                '<div class="card filter-bar">' +
                '<div class="field grow"><label>关键词</label><input class="input" id="tax-type-keyword" placeholder="税种代码 / 名称"></div>' +
                '<div class="field"><label>启用状态</label><select class="select" id="tax-type-status">' + App.optionList([{ v: '启用' }, { v: '停用' }], 'v', 'v', '', '全部状态') + '</select></div>' +
                '<div class="filter-actions"><button type="button" class="btn primary" id="tax-type-search">' + App.icon('query', 15) + '查询</button><button type="button" class="btn ghost" id="tax-type-reset">' + App.icon('refresh', 15) + '重置</button></div>' +
                '</div>' +
                '<div class="card"><div class="table-wrap" id="tax-type-table">' + App.loading() + '</div><div id="tax-type-pager"></div></div>';

            var table = view.querySelector('#tax-type-table');
            var pager = view.querySelector('#tax-type-pager');
            var keyword = view.querySelector('#tax-type-keyword');
            var status = view.querySelector('#tax-type-status');

            function load(page) {
                state.page = page || state.page;
                table.innerHTML = App.loading();
                pager.innerHTML = '';
                API.get('/tax-types' + App.query({
                    keyword: state.keyword,
                    status: state.status,
                    page: state.page,
                    size: state.size
                })).then(function (result) {
                    state.items = result.items || [];
                    if (!state.items.length) {
                        table.innerHTML = App.empty('暂无税种', '可以调整筛选条件或新增税种');
                        return;
                    }
                    table.innerHTML = '<table class="data"><thead><tr><th>税种代码</th><th>税种名称</th><th>税率</th><th>说明</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead><tbody>' +
                        state.items.map(function (item) {
                            return '<tr><td><span class="cell-main mono">' + App.esc(item.code) + '</span></td>' +
                                '<td><span class="cell-main">' + App.esc(item.name) + '</span></td>' +
                                '<td><span class="cell-money">' + App.rate(item.rate) + '</span></td>' +
                                '<td class="muted" style="max-width:300px">' + App.esc(item.description || '—') + '</td>' +
                                '<td>' + App.badge(item.status, { '启用': 'green', '停用': 'gray' }) + '</td>' +
                                '<td><span class="cell-date">' + App.esc(App.dateTime(item.createdAt)) + '</span></td>' +
                                '<td class="actions"><button type="button" class="btn link" data-edit="' + item.id + '">编辑</button><button type="button" class="btn link danger" data-delete="' + item.id + '">删除</button></td></tr>';
                        }).join('') + '</tbody></table>';
                    pager.innerHTML = App.pager(result);
                    App.bindPager(view, result, load);
                    table.querySelectorAll('[data-edit]').forEach(function (button) {
                        button.addEventListener('click', function () {
                            openForm(state.items.filter(function (item) {
                                return item.id === Number(button.getAttribute('data-edit'));
                            })[0]);
                        });
                    });
                    table.querySelectorAll('[data-delete]').forEach(function (button) {
                        button.addEventListener('click', function () {
                            remove(state.items.filter(function (item) {
                                return item.id === Number(button.getAttribute('data-delete'));
                            })[0]);
                        });
                    });
                }).catch(function (error) {
                    table.innerHTML = App.errorBox(error);
                });
            }

            function openForm(item) {
                var editing = !!item;
                var data = item || {};
                var modal = App.openModal({
                    title: editing ? '编辑税种' : '新增税种',
                    body: '<form id="tax-type-form" class="form-grid">' +
                        '<div class="field"><label>税种代码 <span style="color:#d84654">*</span></label><input class="input mono" name="code" maxlength="30" value="' + App.esc(data.code) + '" placeholder="如 VAT" required></div>' +
                        '<div class="field"><label>税种名称 <span style="color:#d84654">*</span></label><input class="input" name="name" maxlength="40" value="' + App.esc(data.name) + '" placeholder="如 增值税" required></div>' +
                        '<div class="field"><label>税率（%） <span style="color:#d84654">*</span></label><input class="input" type="number" name="rate" min="0" max="100" step="0.0001" value="' + App.esc(data.rate === undefined ? '' : data.rate) + '" required><div class="form-hint">例如 13 表示 13%，0.05 表示 0.05%</div></div>' +
                        '<div class="field"><label>启用状态 <span style="color:#d84654">*</span></label><select class="select" name="status" required>' + App.statusOptions(data.status || '启用', ['启用', '停用']) + '</select></div>' +
                        '<div class="field full"><label>税种说明</label><textarea class="textarea" name="description" maxlength="300" placeholder="填写征收范围或计税说明">' + App.esc(data.description) + '</textarea></div>' +
                        '</form>',
                    footer: '<button type="button" class="btn ghost" data-form-cancel>取消</button><button type="button" class="btn primary" id="tax-type-save">' + (editing ? '保存修改' : '确认新增') + '</button>'
                });
                var form = modal.querySelector('#tax-type-form');
                modal.querySelector('[data-form-cancel]').addEventListener('click', App.closeModal);
                modal.querySelector('#tax-type-save').addEventListener('click', function () {
                    if (!form.reportValidity()) {
                        return;
                    }
                    var button = modal.querySelector('#tax-type-save');
                    var payload = App.readForm(form);
                    App.setBusy(button, true, '保存中...');
                    var request = editing ? API.put('/tax-types/' + item.id, payload) : API.post('/tax-types', payload);
                    request.then(function () {
                        App.closeModal();
                        App.toast(editing ? '税种信息已更新' : '税种已创建');
                        load(editing ? state.page : 1);
                    }).catch(function (error) {
                        App.setBusy(button, false);
                        App.toast(error.message, 'error');
                    });
                });
            }

            function remove(item) {
                App.confirm({
                    title: '删除税种',
                    message: '确认删除税种“' + item.name + '”吗？存在申报记录时无法删除。',
                    confirmText: '确认删除',
                    danger: true
                }).then(function (ok) {
                    if (!ok) {
                        return;
                    }
                    API.del('/tax-types/' + item.id).then(function (result) {
                        App.toast((result && result.message) || '税种已删除');
                        load();
                    }).catch(function (error) {
                        App.toast(error.message, 'error');
                    });
                });
            }

            view.querySelector('#tax-type-create').addEventListener('click', function () {
                openForm(null);
            });
            view.querySelector('#tax-type-search').addEventListener('click', function () {
                state.keyword = keyword.value.trim();
                state.status = status.value;
                load(1);
            });
            view.querySelector('#tax-type-reset').addEventListener('click', function () {
                keyword.value = '';
                status.value = '';
                state.keyword = '';
                state.status = '';
                load(1);
            });
            keyword.addEventListener('keydown', function (event) {
                if (event.key === 'Enter') {
                    view.querySelector('#tax-type-search').click();
                }
            });
            load(1);
        }
    };
})();
