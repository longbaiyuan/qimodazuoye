(function () {
    'use strict';

    var App = window.App;
    var taxpayerCache = null;

    function pad(value) {
        return value < 10 ? '0' + value : String(value);
    }

    function isoDate(date) {
        return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
    }

    function loadTaxpayerOptions() {
        if (!taxpayerCache) {
            taxpayerCache = API.get('/taxpayers/options');
        }
        return taxpayerCache;
    }

    function emptyPaymentText() {
        return App.empty('暂无缴款记录', '审核通过的申报完成缴款后会显示在这里');
    }

    function openPayModal(preselectId, onDone) {
        API.get('/declarations/unpaid').then(function (items) {
            var declarations = items || [];
            if (!declarations.length) {
                App.toast('当前没有待缴款的申报', 'info');
                return;
            }
            var selected = preselectId || (declarations[0] && declarations[0].id);
            var modal = App.openModal({
                title: '税款缴纳',
                body: '<div class="info-banner">' + App.icon('info', 17) + '<span>请选择审核通过且尚未缴款的申报，系统将按应纳税额一次性扣缴并生成缴款凭证。</span></div>' +
                    '<form id="payment-form" class="form-grid">' +
                    '<div class="field full"><label>待缴申报 <span style="color:#d84654">*</span></label><select class="select" name="declarationId" id="pay-declaration" required>' +
                    App.optionList(declarations, 'id', function (item) {
                        return item.declarationNo + ' · ' + item.taxpayerName + ' · ' + App.money(item.taxAmount - item.paidAmount);
                    }, selected) + '</select></div>' +
                    '<div class="field"><label>缴款方式 <span style="color:#d84654">*</span></label><select class="select" name="method" required>' +
                    '<option value="银行扣款">银行扣款</option><option value="第三方支付">第三方支付</option><option value="银联在线">银联在线</option><option value="其他">其他</option></select></div>' +
                    '<div class="field"><label>应缴金额</label><input class="input money" id="pay-amount" disabled value=""></div>' +
                    '<div class="field full"><div class="calc-box"><span>本次缴款金额</span><strong id="pay-amount-large">¥ 0.00</strong></div></div>' +
                    '<div class="field full" id="pay-detail"></div>' +
                    '</form>',
                footer: '<button type="button" class="btn ghost" data-form-cancel>取消</button><button type="button" class="btn primary" id="payment-save">' + App.icon('card', 15) + '确认缴款</button>'
            });
            var form = modal.querySelector('#payment-form');
            var select = modal.querySelector('#pay-declaration');
            var amountInput = modal.querySelector('#pay-amount');
            var amountLarge = modal.querySelector('#pay-amount-large');
            var detail = modal.querySelector('#pay-detail');

            function current() {
                return declarations.filter(function (item) {
                    return String(item.id) === String(select.value);
                })[0];
            }

            function refresh() {
                var item = current();
                if (!item) {
                    amountInput.value = '';
                    amountLarge.textContent = App.money(0);
                    detail.innerHTML = '';
                    return;
                }
                var due = Number(item.taxAmount || 0) - Number(item.paidAmount || 0);
                amountInput.value = App.money(due);
                amountLarge.textContent = App.money(due);
                detail.innerHTML = '<div class="detail-grid">' +
                    '<div class="detail-item"><label>纳税人</label><span>' + App.esc(item.taxpayerName) + '</span></div>' +
                    '<div class="detail-item"><label>税种</label><span>' + App.esc(item.taxTypeName) + '</span></div>' +
                    '<div class="detail-item"><label>所属期</label><span>' + App.esc(App.dateOnly(item.periodStart) + ' 至 ' + App.dateOnly(item.periodEnd)) + '</span></div>' +
                    '<div class="detail-item"><label>应纳税额</label><span>' + App.money(item.taxAmount) + '</span></div>' +
                    '</div>';
            }

            select.addEventListener('change', refresh);
            refresh();

            modal.querySelector('[data-form-cancel]').addEventListener('click', App.closeModal);
            modal.querySelector('#payment-save').addEventListener('click', function () {
                if (!form.reportValidity()) {
                    return;
                }
                var data = App.readForm(form);
                var button = modal.querySelector('#payment-save');
                App.setBusy(button, true, '缴款处理中...');
                API.post('/payments', {
                    declarationId: Number(data.declarationId),
                    method: data.method
                }).then(function (result) {
                    App.closeModal();
                    App.toast('缴款成功：' + result.paymentNo + '，金额 ' + App.money(result.amount));
                    if (onDone) {
                        onDone();
                    }
                }).catch(function (error) {
                    App.setBusy(button, false);
                    App.toast(error.message, 'error');
                });
            });
        }).catch(function (error) {
            App.toast(error.message, 'error');
        });
    }

    App.pages.payments = {
        title: '税款缴纳',
        crumb: '业务办理 / 税款缴纳',
        render: function (view) {
            var state = { keyword: '', page: 1, size: 10, items: [] };

            view.innerHTML =
                '<div class="page-head"><div><h3>税款缴纳</h3><p>查询缴款凭证，对审核通过的申报办理税款缴纳。</p></div>' +
                '<div class="page-actions"><button type="button" class="btn primary" id="payment-create">' + App.icon('card', 15) + '办理缴款</button></div></div>' +
                '<div class="card filter-bar"><div class="field grow"><label>关键词</label><input class="input" id="payment-keyword" placeholder="缴款凭证号 / 申报编号 / 纳税人"></div>' +
                '<div class="filter-actions"><button type="button" class="btn primary" id="payment-search">' + App.icon('query', 15) + '查询</button><button type="button" class="btn ghost" id="payment-reset">' + App.icon('refresh', 15) + '重置</button></div></div>' +
                '<div class="card"><div class="table-wrap" id="payment-table">' + App.loading() + '</div><div id="payment-pager"></div></div>';

            var table = view.querySelector('#payment-table');
            var pager = view.querySelector('#payment-pager');
            var keyword = view.querySelector('#payment-keyword');

            function load(page) {
                state.page = page || state.page;
                table.innerHTML = App.loading();
                pager.innerHTML = '';
                API.get('/payments' + App.query({
                    keyword: state.keyword,
                    page: state.page,
                    size: state.size
                })).then(function (result) {
                    state.items = result.items || [];
                    if (!state.items.length) {
                        table.innerHTML = emptyPaymentText();
                        return;
                    }
                    table.innerHTML = '<table class="data"><thead><tr><th>缴款凭证号</th><th>申报编号</th><th>纳税人 / 税种</th><th>缴款金额</th><th>缴款方式</th><th>缴款日期</th><th>操作人</th><th>状态</th></tr></thead><tbody>' +
                        state.items.map(function (item) {
                            return '<tr><td><span class="cell-main mono">' + App.esc(item.paymentNo) + '</span></td>' +
                                '<td><span class="mono" style="color:#4f6784">' + App.esc(item.declarationNo) + '</span></td>' +
                                '<td><div class="cell-main">' + App.esc(item.taxpayerName) + '</div><div class="cell-sub">' + App.esc(item.taxTypeName) + '</div></td>' +
                                '<td><span class="cell-money">' + App.money(item.amount) + '</span></td>' +
                                '<td>' + App.esc(item.method) + '</td>' +
                                '<td><span class="cell-date">' + App.esc(App.dateTime(item.paymentDate)) + '</span></td>' +
                                '<td>' + App.esc(item.operator || '—') + '</td>' +
                                '<td><span class="badge green">' + App.esc(item.status) + '</span></td></tr>';
                        }).join('') + '</tbody></table>';
                    pager.innerHTML = App.pager(result);
                    App.bindPager(view, result, load);
                }).catch(function (error) {
                    table.innerHTML = App.errorBox(error);
                });
            }

            view.querySelector('#payment-create').addEventListener('click', function () {
                openPayModal(null, function () {
                    load(1);
                });
            });
            view.querySelector('#payment-search').addEventListener('click', function () {
                state.keyword = keyword.value.trim();
                load(1);
            });
            view.querySelector('#payment-reset').addEventListener('click', function () {
                keyword.value = '';
                state.keyword = '';
                load(1);
            });
            keyword.addEventListener('keydown', function (event) {
                if (event.key === 'Enter') {
                    view.querySelector('#payment-search').click();
                }
            });
            load(1);
        }
    };

    function invoiceTypes(selected) {
        return ['增值税专用发票', '增值税普通发票', '电子普通发票', '机动车销售统一发票', '其他'].map(function (type) {
            return '<option value="' + type + '"' + (type === selected ? ' selected' : '') + '>' + type + '</option>';
        }).join('');
    }

    function invoiceForm(user, item, onDone) {
        loadTaxpayerOptions().then(function (taxpayers) {
            var editing = !!item;
            var data = item || {};
            var modal = App.openModal({
                title: editing ? '编辑发票' : '开具发票',
                wide: true,
                body: '<form id="invoice-form" class="form-grid">' +
                    '<div class="field full"><label>开票纳税人 <span style="color:#d84654">*</span></label><select class="select" name="taxpayerId" required>' +
                    App.optionList(taxpayers || [], 'id', function (taxpayer) {
                        return taxpayer.name + (taxpayer.taxpayerNo ? '（' + taxpayer.taxpayerNo + '）' : '');
                    }, data.taxpayerId, '请选择纳税人') + '</select></div>' +
                    '<div class="field"><label>发票类型 <span style="color:#d84654">*</span></label><select class="select" name="invoiceType" required>' + invoiceTypes(data.invoiceType || '增值税专用发票') + '</select></div>' +
                    '<div class="field"><label>开票日期 <span style="color:#d84654">*</span></label><input class="input" type="date" name="issueDate" value="' + App.esc(data.issueDate || isoDate(new Date())) + '" required></div>' +
                    '<div class="field"><label>开票金额（元） <span style="color:#d84654">*</span></label><input class="input" type="number" name="amount" min="0.01" step="0.01" value="' + App.esc(data.amount === undefined ? '' : data.amount) + '" required></div>' +
                    '<div class="field"><label>税额（元） <span style="color:#d84654">*</span></label><input class="input" type="number" name="taxAmount" min="0" step="0.01" value="' + App.esc(data.taxAmount === undefined ? '' : data.taxAmount) + '" required></div>' +
                    '<div class="field full"><label>备注</label><textarea class="textarea" name="remark" maxlength="300" placeholder="填写开票项目或说明">' + App.esc(data.remark) + '</textarea></div>' +
                    '</form>',
                footer: '<button type="button" class="btn ghost" data-form-cancel>取消</button><button type="button" class="btn primary" id="invoice-save">' + (editing ? '保存修改' : '确认开具') + '</button>'
            });
            var form = modal.querySelector('#invoice-form');
            modal.querySelector('[data-form-cancel]').addEventListener('click', App.closeModal);
            modal.querySelector('#invoice-save').addEventListener('click', function () {
                if (!form.reportValidity()) {
                    return;
                }
                var raw = App.readForm(form);
                var payload = {
                    taxpayerId: raw.taxpayerId ? Number(raw.taxpayerId) : null,
                    invoiceType: raw.invoiceType,
                    amount: raw.amount,
                    taxAmount: raw.taxAmount,
                    issueDate: raw.issueDate,
                    remark: raw.remark
                };
                var button = modal.querySelector('#invoice-save');
                App.setBusy(button, true, '保存中...');
                var request = editing ? API.put('/invoices/' + item.id, payload) : API.post('/invoices', payload);
                request.then(function (result) {
                    App.closeModal();
                    App.toast(editing ? '发票信息已更新' : '发票开具成功：' + result.invoiceNo);
                    if (onDone) {
                        onDone();
                    }
                }).catch(function (error) {
                    App.setBusy(button, false);
                    App.toast(error.message, 'error');
                });
            });
        }).catch(function (error) {
            App.toast(error.message, 'error');
        });
    }

    App.pages.invoices = {
        title: '发票管理',
        crumb: '业务办理 / 发票管理',
        render: function (view, user) {
            var state = { keyword: '', status: '', page: 1, size: 10, items: [] };
            var manager = user.role === 'ADMIN' || user.role === 'AUDITOR';
            var canDelete = user.role === 'ADMIN';

            view.innerHTML =
                '<div class="page-head"><div><h3>发票管理</h3><p>查询开票记录，管理发票开具、作废与维护。</p></div>' +
                (manager ? '<div class="page-actions"><button type="button" class="btn primary" id="invoice-create">' + App.icon('plus', 15) + '开具发票</button></div>' : '') + '</div>' +
                '<div class="card filter-bar"><div class="field grow"><label>关键词</label><input class="input" id="invoice-keyword" placeholder="发票号码 / 纳税人 / 备注"></div>' +
                '<div class="field"><label>发票状态</label><select class="select" id="invoice-status">' + App.optionList([{ v: '正常' }, { v: '作废' }], 'v', 'v', '', '全部状态') + '</select></div>' +
                '<div class="filter-actions"><button type="button" class="btn primary" id="invoice-search">' + App.icon('query', 15) + '查询</button><button type="button" class="btn ghost" id="invoice-reset">' + App.icon('refresh', 15) + '重置</button></div></div>' +
                '<div class="card"><div class="table-wrap" id="invoice-table">' + App.loading() + '</div><div id="invoice-pager"></div></div>';

            var table = view.querySelector('#invoice-table');
            var pager = view.querySelector('#invoice-pager');
            var keyword = view.querySelector('#invoice-keyword');
            var status = view.querySelector('#invoice-status');

            function load(page) {
                state.page = page || state.page;
                table.innerHTML = App.loading();
                pager.innerHTML = '';
                API.get('/invoices' + App.query({
                    keyword: state.keyword,
                    status: state.status,
                    page: state.page,
                    size: state.size
                })).then(function (result) {
                    state.items = result.items || [];
                    if (!state.items.length) {
                        table.innerHTML = App.empty('暂无发票记录', '开具发票或调整筛选条件后查看');
                        return;
                    }
                    table.innerHTML = '<table class="data"><thead><tr><th>发票号码</th><th>纳税人</th><th>发票类型</th><th>开票金额</th><th>税额</th><th>开票日期</th><th>状态</th><th>备注</th><th>操作</th></tr></thead><tbody>' +
                        state.items.map(function (item) {
                            var editable = manager && item.status === '正常';
                            return '<tr><td><span class="cell-main mono">' + App.esc(item.invoiceNo) + '</span></td>' +
                                '<td><div class="cell-main">' + App.esc(item.taxpayerName) + '</div><div class="cell-sub">' + App.esc(item.invoiceType) + '</div></td>' +
                                '<td>' + App.esc(item.invoiceType) + '</td>' +
                                '<td><span class="cell-money">' + App.money(item.amount) + '</span></td>' +
                                '<td><span class="cell-money">' + App.money(item.taxAmount) + '</span></td>' +
                                '<td><span class="cell-date">' + App.esc(App.dateOnly(item.issueDate)) + '</span></td>' +
                                '<td>' + App.invoiceBadge(item.status) + '</td>' +
                                '<td class="muted" style="max-width:220px">' + App.esc(item.remark || '—') + '</td>' +
                                '<td class="actions"><button type="button" class="btn link" data-view="' + item.id + '">详情</button>' +
                                (editable ? '<button type="button" class="btn link" data-edit="' + item.id + '">编辑</button><button type="button" class="btn link danger" data-void="' + item.id + '">作废</button>' : '') +
                                (canDelete ? '<button type="button" class="btn link danger" data-delete="' + item.id + '">删除</button>' : '') + '</td></tr>';
                        }).join('') + '</tbody></table>';
                    pager.innerHTML = App.pager(result);
                    App.bindPager(view, result, load);
                    table.querySelectorAll('[data-view]').forEach(function (button) {
                        button.addEventListener('click', function () {
                            showInvoice(Number(button.getAttribute('data-view')));
                        });
                    });
                    table.querySelectorAll('[data-edit]').forEach(function (button) {
                        button.addEventListener('click', function () {
                            invoiceForm(user, findItem(button.getAttribute('data-edit')), function () {
                                load();
                            });
                        });
                    });
                    table.querySelectorAll('[data-void]').forEach(function (button) {
                        button.addEventListener('click', function () {
                            var item = findItem(button.getAttribute('data-void'));
                            App.confirm({
                                title: '作废发票',
                                message: '确认作废发票“' + item.invoiceNo + '”吗？作废后不能恢复。',
                                confirmText: '确认作废',
                                danger: true
                            }).then(function (ok) {
                                if (!ok) {
                                    return;
                                }
                                API.post('/invoices/' + item.id + '/void').then(function () {
                                    App.toast('发票已作废');
                                    load();
                                }).catch(function (error) {
                                    App.toast(error.message, 'error');
                                });
                            });
                        });
                    });
                    table.querySelectorAll('[data-delete]').forEach(function (button) {
                        button.addEventListener('click', function () {
                            var item = findItem(button.getAttribute('data-delete'));
                            App.confirm({
                                title: '删除发票',
                                message: '确认删除发票“' + item.invoiceNo + '”吗？该操作不可恢复。',
                                confirmText: '确认删除',
                                danger: true
                            }).then(function (ok) {
                                if (!ok) {
                                    return;
                                }
                                API.del('/invoices/' + item.id).then(function (result) {
                                    App.toast((result && result.message) || '发票已删除');
                                    load();
                                }).catch(function (error) {
                                    App.toast(error.message, 'error');
                                });
                            });
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

            function showInvoice(id) {
                API.get('/invoices/' + id).then(function (item) {
                    var modal = App.openModal({
                        title: '发票详情 · ' + item.invoiceNo,
                        body: '<div class="amount-strip">' +
                            '<div class="amount-cell"><label>开票金额</label><strong>' + App.money(item.amount) + '</strong></div>' +
                            '<div class="amount-cell highlight"><label>税额</label><strong>' + App.money(item.taxAmount) + '</strong></div>' +
                            '<div class="amount-cell"><label>价税合计</label><strong>' + App.money(Number(item.amount) + Number(item.taxAmount)) + '</strong></div></div>' +
                            '<div class="detail-grid">' +
                            '<div class="detail-item"><label>发票号码</label><span class="mono">' + App.esc(item.invoiceNo) + '</span></div>' +
                            '<div class="detail-item"><label>发票状态</label><span>' + App.esc(item.status) + '</span></div>' +
                            '<div class="detail-item"><label>纳税人</label><span>' + App.esc(item.taxpayerName) + '</span></div>' +
                            '<div class="detail-item"><label>发票类型</label><span>' + App.esc(item.invoiceType) + '</span></div>' +
                            '<div class="detail-item"><label>开票日期</label><span>' + App.esc(App.dateOnly(item.issueDate)) + '</span></div>' +
                            '<div class="detail-item"><label>创建时间</label><span>' + App.esc(App.dateTime(item.createdAt)) + '</span></div>' +
                            '<div class="detail-item full"><label>备注</label><span>' + App.esc(item.remark || '—') + '</span></div></div>',
                        footer: '<button type="button" class="btn ghost" data-detail-close>关闭</button>' + (manager && item.status === '正常' ? '<button type="button" class="btn primary" data-detail-edit>编辑发票</button>' : '')
                    });
                    modal.querySelector('[data-detail-close]').addEventListener('click', App.closeModal);
                    var edit = modal.querySelector('[data-detail-edit]');
                    if (edit) {
                        edit.addEventListener('click', function () {
                            App.closeModal();
                            invoiceForm(user, item, function () {
                                load();
                            });
                        });
                    }
                }).catch(function (error) {
                    App.toast(error.message, 'error');
                });
            }

            var createButton = view.querySelector('#invoice-create');
            if (createButton) {
                createButton.addEventListener('click', function () {
                    invoiceForm(user, null, function () {
                        load(1);
                    });
                });
            }
            view.querySelector('#invoice-search').addEventListener('click', function () {
                state.keyword = keyword.value.trim();
                state.status = status.value;
                load(1);
            });
            view.querySelector('#invoice-reset').addEventListener('click', function () {
                keyword.value = '';
                status.value = '';
                state.keyword = '';
                state.status = '';
                load(1);
            });
            keyword.addEventListener('keydown', function (event) {
                if (event.key === 'Enter') {
                    view.querySelector('#invoice-search').click();
                }
            });
            load(1);
        }
    };

    App.actions = App.actions || {};
    App.actions.payDeclaration = function (id) {
        openPayModal(id, function () {
            App.renderRoute();
        });
    };
})();
