(function () {
    'use strict';

    var App = window.App;
    var cache = { taxTypes: null, taxpayers: null };

    function pad(value) {
        return value < 10 ? '0' + value : String(value);
    }

    function isoDate(date) {
        return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
    }

    function currentMonthStart() {
        var now = new Date();
        return isoDate(new Date(now.getFullYear(), now.getMonth(), 1));
    }

    function today() {
        return isoDate(new Date());
    }

    function loadTaxTypes() {
        if (!cache.taxTypes) {
            cache.taxTypes = API.get('/tax-types/options');
        }
        return cache.taxTypes;
    }

    function loadTaxpayers(user) {
        if (user.role === 'TAXPAYER') {
            return Promise.resolve(user.taxpayerId ? [{
                id: user.taxpayerId,
                name: user.taxpayerName || '当前纳税人',
                taxpayerNo: '',
                status: '正常'
            }] : []);
        }
        if (!cache.taxpayers) {
            cache.taxpayers = API.get('/taxpayers/options');
        }
        return cache.taxpayers;
    }

    function taxTypeLabel(item) {
        return item.name + ' · ' + App.rate(item.rate);
    }

    function summaryCard(icon, color, label, value, hint) {
        return '<div class="stat-card"><div class="stat-icon ' + color + '">' + App.icon(icon, 19) + '</div><div class="stat-body"><div class="label">' + App.esc(label) + '</div><div class="value">' + value + '</div>' + (hint ? '<div class="hint">' + App.esc(hint) + '</div>' : '') + '</div></div>';
    }

    function detailItem(label, value, full) {
        return '<div class="detail-item' + (full ? ' full' : '') + '"><label>' + App.esc(label) + '</label><span>' + App.esc(value || '—') + '</span></div>';
    }

    function declarationForm(user, item, onSaved) {
        Promise.all([loadTaxTypes(), loadTaxpayers(user)]).then(function (results) {
            var taxTypes = results[0] || [];
            var taxpayers = results[1] || [];
            var editing = !!item;
            var data = item || {};
            var selectedTaxpayer = data.taxpayerId || user.taxpayerId || (taxpayers[0] && taxpayers[0].id);
            if (!taxTypes.length) {
                App.toast('当前没有可用税种，请先在税种管理中启用税种', 'error');
                return;
            }
            var taxpayerControl;
            if (user.role === 'TAXPAYER') {
                taxpayerControl = '<input class="input" value="' + App.esc(user.taxpayerName || '未绑定纳税人') + '" disabled><input type="hidden" name="taxpayerId" value="' + App.esc(user.taxpayerId || '') + '"><div class="form-hint">纳税人账号默认以绑定主体进行申报</div>';
            } else {
                taxpayerControl = '<select class="select" name="taxpayerId" required>' + App.optionList(taxpayers, 'id', function (taxpayer) {
                    return taxpayer.name + (taxpayer.taxpayerNo ? '（' + taxpayer.taxpayerNo + '）' : '');
                }, selectedTaxpayer, '请选择纳税人') + '</select>';
            }
            var modal = App.openModal({
                title: editing ? '编辑纳税申报' : '新增纳税申报',
                wide: true,
                body: '<form id="declaration-form" class="form-grid">' +
                    '<div class="field full"><label>纳税人 <span style="color:#d84654">*</span></label>' + taxpayerControl + '</div>' +
                    '<div class="field"><label>税种 <span style="color:#d84654">*</span></label><select class="select" name="taxTypeId" id="decl-tax-type" required>' + App.optionList(taxTypes, 'id', taxTypeLabel, data.taxTypeId, '请选择税种') + '</select></div>' +
                    '<div class="field"><label>适用税率</label><input class="input" id="decl-rate" value="" disabled></div>' +
                    '<div class="field"><label>所属期起 <span style="color:#d84654">*</span></label><input class="input" type="date" name="periodStart" value="' + App.esc(data.periodStart || currentMonthStart()) + '" required></div>' +
                    '<div class="field"><label>所属期止 <span style="color:#d84654">*</span></label><input class="input" type="date" name="periodEnd" value="' + App.esc(data.periodEnd || today()) + '" required></div>' +
                    '<div class="field full"><label>计税金额（元） <span style="color:#d84654">*</span></label><input class="input" type="number" name="taxableAmount" id="decl-taxable" min="0" step="0.01" value="' + App.esc(data.taxableAmount === undefined ? '0.00' : data.taxableAmount) + '" required></div>' +
                    '<div class="field full"><div class="calc-box"><span>系统自动计算应纳税额</span><strong id="decl-amount">¥ 0.00</strong></div><div class="form-hint">税额 = 计税金额 × 税率 ÷ 100，保存后以系统计算结果为准。</div></div>' +
                    '<div class="field full"><div class="form-tip"><strong>提示：</strong>新建申报将保存为草稿，确认无误后可在列表提交审核。已驳回的申报可修改后重新提交。</div></div>' +
                    '</form>',
                footer: '<button type="button" class="btn ghost" data-form-cancel>取消</button><button type="button" class="btn primary" id="declaration-save">' + (editing ? '保存修改' : '保存草稿') + '</button>'
            });
            var form = modal.querySelector('#declaration-form');
            var typeSelect = modal.querySelector('#decl-tax-type');
            var taxable = modal.querySelector('#decl-taxable');
            var rateInput = modal.querySelector('#decl-rate');
            var amountText = modal.querySelector('#decl-amount');

            function calculate() {
                var taxType = taxTypes.filter(function (type) {
                    return String(type.id) === String(typeSelect.value);
                })[0];
                var rate = taxType ? Number(taxType.rate || 0) : 0;
                var amount = Number(taxable.value || 0);
                rateInput.value = taxType ? App.rate(rate) : '';
                amountText.textContent = App.money(amount * rate / 100);
            }

            typeSelect.addEventListener('change', calculate);
            taxable.addEventListener('input', calculate);
            calculate();

            modal.querySelector('[data-form-cancel]').addEventListener('click', App.closeModal);
            modal.querySelector('#declaration-save').addEventListener('click', function () {
                if (!form.reportValidity()) {
                    return;
                }
                var raw = App.readForm(form);
                var payload = {
                    taxpayerId: raw.taxpayerId ? Number(raw.taxpayerId) : null,
                    taxTypeId: raw.taxTypeId ? Number(raw.taxTypeId) : null,
                    periodStart: raw.periodStart,
                    periodEnd: raw.periodEnd,
                    taxableAmount: raw.taxableAmount
                };
                var button = modal.querySelector('#declaration-save');
                App.setBusy(button, true, '保存中...');
                var request = editing ? API.put('/declarations/' + item.id, payload) : API.post('/declarations', payload);
                request.then(function (result) {
                    App.closeModal();
                    App.toast(editing ? '申报信息已更新' : '申报草稿已创建：' + result.declarationNo);
                    if (onSaved) {
                        onSaved();
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

    function submitDeclaration(id, onDone) {
        App.confirm({
            title: '提交申报审核',
            message: '确认将该申报提交审核吗？提交后在审核完成前不能修改。',
            confirmText: '提交审核'
        }).then(function (ok) {
            if (!ok) {
                return;
            }
            API.post('/declarations/' + id + '/submit').then(function () {
                App.toast('申报已提交审核');
                if (onDone) {
                    onDone();
                }
            }).catch(function (error) {
                App.toast(error.message, 'error');
            });
        });
    }

    function reviewDeclaration(id, declarationNo, approve, onDone) {
        var action = approve ? '通过' : '驳回';
        var modal = App.openModal({
            title: '审核申报 · ' + action,
            body: '<div class="info-banner">' + App.icon('info', 17) + '<span>申报编号：<strong class="mono">' + App.esc(declarationNo || '') + '</strong>。' + (approve ? '审核通过后纳税人可进行缴款。' : '请填写明确的驳回原因，便于纳税人修改后重新提交。') + '</span></div>' +
                '<form id="review-form"><div class="field"><label>审核意见' + (approve ? '' : ' <span style="color:#d84654">*</span>') + '</label><textarea class="textarea" name="comment" maxlength="300" placeholder="' + (approve ? '可填写审核备注（选填）' : '请填写驳回原因') + '"></textarea></div></form>',
            footer: '<button type="button" class="btn ghost" data-form-cancel>取消</button><button type="button" class="btn ' + (approve ? 'success' : 'danger') + '" id="review-save">' + (approve ? '确认通过' : '确认驳回') + '</button>'
        });
        var form = modal.querySelector('#review-form');
        var textarea = form.querySelector('[name="comment"]');
        if (!approve) {
            textarea.setAttribute('required', 'required');
        }
        modal.querySelector('[data-form-cancel]').addEventListener('click', App.closeModal);
        modal.querySelector('#review-save').addEventListener('click', function () {
            if (!form.reportValidity()) {
                return;
            }
            var button = modal.querySelector('#review-save');
            App.setBusy(button, true, '提交中...');
            API.post('/audits/' + id + (approve ? '/approve' : '/reject'), { comment: textarea.value.trim() }).then(function () {
                App.closeModal();
                App.toast('申报已' + action);
                if (onDone) {
                    onDone();
                }
            }).catch(function (error) {
                App.setBusy(button, false);
                App.toast(error.message, 'error');
            });
        });
    }

    function showDeclarationDetail(id, user, onChanged) {
        API.get('/declarations/' + id).then(function (item) {
            return API.get('/audits/records' + App.query({ declarationId: id, page: 1, size: 100 })).then(function (records) {
                return { declaration: item, records: records };
            });
        }).then(function (result) {
            var item = result.declaration;
            var records = (result.records.items || []).slice().sort(function (a, b) {
                return String(a.createdAt || '').localeCompare(String(b.createdAt || ''));
            });
            var canAudit = user.role === 'ADMIN' || user.role === 'AUDITOR';
            var editable = item.status === '草稿' || item.status === '已驳回';
            var footer = '<button type="button" class="btn ghost" data-detail-close>关闭</button>';
            if (editable) {
                footer += '<button type="button" class="btn ghost" data-detail-edit>编辑申报</button><button type="button" class="btn primary" data-detail-submit>提交审核</button>';
            }
            if (item.status === '待审核' && canAudit) {
                footer += '<button type="button" class="btn danger" data-detail-reject>驳回</button><button type="button" class="btn success" data-detail-approve>审核通过</button>';
            }
            if (item.status === '已通过') {
                footer += '<button type="button" class="btn accent" data-detail-pay>前往缴款</button>';
            }
            var timeline = records.length ? '<div class="timeline">' + records.map(function (record) {
                var cls = record.action === '通过' ? 'green' : (record.action === '驳回' ? 'red' : (record.action === '缴款' ? 'orange' : 'blue'));
                return '<div class="timeline-item"><span class="timeline-dot ' + cls + '"></span><div class="timeline-body"><div class="timeline-top"><strong>' + App.esc(record.action) + '</strong><span>' + App.esc(App.dateTime(record.createdAt)) + '</span></div>' +
                    '<p>' + App.esc(record.comment || '无补充说明') + '</p><small>操作人：' + App.esc(record.auditor || '—') + '</small></div></div>';
            }).join('') + '</div>' : '<div class="muted" style="padding:8px 0 10px">当前申报尚无流转记录。</div>';

            var modal = App.openModal({
                title: '申报详情 · ' + item.declarationNo,
                wide: true,
                body: '<div class="amount-strip">' +
                    '<div class="amount-cell"><label>计税金额</label><strong>' + App.money(item.taxableAmount) + '</strong></div>' +
                    '<div class="amount-cell highlight"><label>应纳税额</label><strong>' + App.money(item.taxAmount) + '</strong></div>' +
                    '<div class="amount-cell"><label>已缴税额</label><strong>' + App.money(item.paidAmount) + '</strong></div></div>' +
                    '<div class="detail-section-title">申报信息</div><div class="detail-grid">' +
                    detailItem('申报编号', item.declarationNo) + detailItem('当前状态', item.status) +
                    detailItem('纳税人名称', item.taxpayerName) + detailItem('纳税人识别号', item.taxpayerNo) +
                    detailItem('税种', item.taxTypeName + '（' + item.taxTypeCode + '）') + detailItem('适用税率', App.rate(item.taxRate)) +
                    detailItem('所属期起', App.dateOnly(item.periodStart)) + detailItem('所属期止', App.dateOnly(item.periodEnd)) +
                    detailItem('创建时间', App.dateTime(item.createdAt)) + detailItem('更新时间', App.dateTime(item.updatedAt)) +
                    detailItem('提交时间', App.dateTime(item.submitTime)) + detailItem('审核人', item.reviewer) +
                    detailItem('审核时间', App.dateTime(item.reviewTime)) + detailItem('审核意见', item.reviewComment, true) +
                    '</div><div class="detail-section-title">流转记录</div>' + timeline,
                footer: footer
            });
            modal.querySelector('[data-detail-close]').addEventListener('click', App.closeModal);
            var refresh = function () {
                if (onChanged) {
                    onChanged();
                }
            };
            var editButton = modal.querySelector('[data-detail-edit]');
            if (editButton) {
                editButton.addEventListener('click', function () {
                    App.closeModal();
                    declarationForm(user, item, refresh);
                });
            }
            var submitButton = modal.querySelector('[data-detail-submit]');
            if (submitButton) {
                submitButton.addEventListener('click', function () {
                    App.closeModal();
                    submitDeclaration(item.id, refresh);
                });
            }
            var approveButton = modal.querySelector('[data-detail-approve]');
            if (approveButton) {
                approveButton.addEventListener('click', function () {
                    App.closeModal();
                    reviewDeclaration(item.id, item.declarationNo, true, refresh);
                });
            }
            var rejectButton = modal.querySelector('[data-detail-reject]');
            if (rejectButton) {
                rejectButton.addEventListener('click', function () {
                    App.closeModal();
                    reviewDeclaration(item.id, item.declarationNo, false, refresh);
                });
            }
            var payButton = modal.querySelector('[data-detail-pay]');
            if (payButton) {
                payButton.addEventListener('click', function () {
                    App.closeModal();
                    App.go('payments');
                    App.toast('请在缴款页面选择该申报完成缴款', 'info');
                });
            }
        }).catch(function (error) {
            App.toast(error.message, 'error');
        });
    }

    App.pages.declarations = {
        title: '纳税申报',
        crumb: '业务办理 / 纳税申报',
        render: function (view, user) {
            var state = {
                keyword: '',
                status: '',
                taxTypeId: '',
                taxpayerId: '',
                startDate: '',
                endDate: '',
                page: 1,
                size: 10,
                items: []
            };
            var isTaxpayer = user.role === 'TAXPAYER';
            var filterHtml = '<div class="card filter-bar">' +
                '<div class="field grow"><label>关键词</label><input class="input" id="decl-keyword" placeholder="申报编号 / 纳税人名称 / 识别号"></div>' +
                '<div class="field"><label>申报状态</label><select class="select" id="decl-status">' + App.declarationStatusOptions('', '全部状态') + '</select></div>' +
                '<div class="field"><label>税种</label><select class="select" id="decl-tax-type"><option value="">全部税种</option></select></div>' +
                (isTaxpayer ? '' : '<div class="field"><label>纳税人</label><select class="select" id="decl-taxpayer"><option value="">全部纳税人</option></select></div>') +
                '<div class="field"><label>所属期起</label><input class="input" type="date" id="decl-start"></div>' +
                '<div class="field"><label>所属期止</label><input class="input" type="date" id="decl-end"></div>' +
                '<div class="filter-actions"><button type="button" class="btn primary" id="decl-search">' + App.icon('query', 15) + '查询</button><button type="button" class="btn ghost" id="decl-reset">' + App.icon('refresh', 15) + '重置</button></div>' +
                '</div>';

            view.innerHTML =
                '<div class="page-head"><div><h3>纳税申报</h3><p>录入计税依据、提交审核并跟踪申报流转状态。</p></div>' +
                '<div class="page-actions"><button type="button" class="btn primary" id="decl-create">' + App.icon('plus', 15) + '新增申报</button></div></div>' +
                '<div class="stat-grid four" id="decl-summary"></div>' +
                '<div id="decl-status-chips" class="status-chips"></div>' +
                filterHtml +
                '<div class="card"><div class="table-wrap" id="decl-table">' + App.loading() + '</div><div id="decl-pager"></div></div>';

            var table = view.querySelector('#decl-table');
            var pager = view.querySelector('#decl-pager');
            var keyword = view.querySelector('#decl-keyword');
            var status = view.querySelector('#decl-status');
            var taxType = view.querySelector('#decl-tax-type');
            var taxpayer = view.querySelector('#decl-taxpayer');
            var start = view.querySelector('#decl-start');
            var end = view.querySelector('#decl-end');

            loadTaxTypes().then(function (items) {
                taxType.innerHTML = '<option value="">全部税种</option>' + App.optionList(items, 'id', taxTypeLabel, '');
            }).catch(function () {
                return null;
            });
            if (taxpayer) {
                loadTaxpayers(user).then(function (items) {
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
                    endDate: state.endDate
                };
            }

            function load(page) {
                state.page = page || state.page;
                table.innerHTML = App.loading();
                pager.innerHTML = '';
                var listRequest = API.get('/declarations' + App.query(Object.assign(params(), {
                    page: state.page,
                    size: state.size
                })));
                var summaryRequest = API.get('/declarations/summary' + App.query(params())).catch(function () {
                    return null;
                });
                Promise.all([listRequest, summaryRequest]).then(function (results) {
                    var result = results[0];
                    var summary = results[1];
                    state.items = result.items || [];
                    renderSummary(summary);
                    if (!state.items.length) {
                        table.innerHTML = App.empty('暂无申报记录', '可以调整筛选条件或新增一条纳税申报');
                        return;
                    }
                    table.innerHTML = '<table class="data"><thead><tr><th>申报编号</th><th>纳税人</th><th>税种</th><th>所属期</th><th>计税金额</th><th>应纳税额</th><th>状态</th><th>提交时间</th><th>操作</th></tr></thead><tbody>' +
                        state.items.map(function (item) {
                            var editable = item.status === '草稿' || item.status === '已驳回';
                            return '<tr><td><div class="cell-main mono">' + App.esc(item.declarationNo) + '</div><div class="cell-sub">' + App.esc(App.dateTime(item.createdAt)) + '</div></td>' +
                                '<td><div class="cell-main">' + App.esc(item.taxpayerName) + '</div><div class="cell-sub mono">' + App.esc(item.taxpayerNo) + '</div></td>' +
                                '<td><div class="cell-main" style="font-weight:500">' + App.esc(item.taxTypeName) + '</div><div class="cell-sub">' + App.esc(item.taxTypeCode) + ' · ' + App.rate(item.taxRate) + '</div></td>' +
                                '<td><span class="cell-date">' + App.esc(App.dateOnly(item.periodStart)) + ' 至 ' + App.esc(App.dateOnly(item.periodEnd)) + '</span></td>' +
                                '<td><span class="cell-money">' + App.money(item.taxableAmount) + '</span></td>' +
                                '<td><span class="cell-money">' + App.money(item.taxAmount) + '</span></td>' +
                                '<td>' + App.declarationBadge(item.status) + '</td>' +
                                '<td><span class="cell-date">' + App.esc(App.dateTime(item.submitTime)) + '</span></td>' +
                                '<td class="actions"><button type="button" class="btn link" data-view="' + item.id + '">详情</button>' +
                                (editable ? '<button type="button" class="btn link" data-edit="' + item.id + '">编辑</button><button type="button" class="btn link" data-submit="' + item.id + '">提交</button>' : '') +
                                (editable ? '<button type="button" class="btn link danger" data-delete="' + item.id + '">删除</button>' : '') +
                                '</td></tr>';
                        }).join('') + '</tbody></table>';
                    pager.innerHTML = App.pager(result);
                    App.bindPager(view, result, load);
                    table.querySelectorAll('[data-view]').forEach(function (button) {
                        button.addEventListener('click', function () {
                            showDeclarationDetail(Number(button.getAttribute('data-view')), user, function () {
                                load();
                            });
                        });
                    });
                    table.querySelectorAll('[data-edit]').forEach(function (button) {
                        button.addEventListener('click', function () {
                            var item = findItem(button.getAttribute('data-edit'));
                            declarationForm(user, item, function () {
                                load();
                            });
                        });
                    });
                    table.querySelectorAll('[data-submit]').forEach(function (button) {
                        button.addEventListener('click', function () {
                            submitDeclaration(Number(button.getAttribute('data-submit')), function () {
                                load();
                            });
                        });
                    });
                    table.querySelectorAll('[data-delete]').forEach(function (button) {
                        button.addEventListener('click', function () {
                            removeItem(findItem(button.getAttribute('data-delete')));
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

            function renderSummary(summary) {
                var box = view.querySelector('#decl-summary');
                var chips = view.querySelector('#decl-status-chips');
                if (!summary) {
                    box.innerHTML = '';
                    chips.innerHTML = '';
                    return;
                }
                box.innerHTML =
                    summaryCard('declarations', 'blue', '申报笔数', App.num(summary.count, 0), '当前筛选范围') +
                    summaryCard('card', 'navy', '计税金额', App.money(summary.taxableTotal), '合计计税依据') +
                    summaryCard('tax', 'orange', '应纳税额', App.money(summary.taxTotal), '应征税额合计') +
                    summaryCard('payment', 'green', '已缴税额', App.money(summary.paidTotal), '已入库税额');
                chips.innerHTML = (summary.statusStats || []).map(function (item) {
                    return '<span class="status-chip">' + App.declarationBadge(item.status) + '<strong>' + App.num(item.count, 0) + '</strong></span>';
                }).join('');
            }

            function removeItem(item) {
                App.confirm({
                    title: '删除申报',
                    message: '确认删除申报“' + item.declarationNo + '”吗？只有草稿或已驳回的申报可以删除。',
                    confirmText: '确认删除',
                    danger: true
                }).then(function (ok) {
                    if (!ok) {
                        return;
                    }
                    API.del('/declarations/' + item.id).then(function (result) {
                        App.toast((result && result.message) || '申报记录已删除');
                        load();
                    }).catch(function (error) {
                        App.toast(error.message, 'error');
                    });
                });
            }

            view.querySelector('#decl-create').addEventListener('click', function () {
                declarationForm(user, null, function () {
                    load(1);
                });
            });
            view.querySelector('#decl-search').addEventListener('click', function () {
                state.keyword = keyword.value.trim();
                state.status = status.value;
                state.taxTypeId = taxType.value;
                state.taxpayerId = taxpayer ? taxpayer.value : '';
                state.startDate = start.value;
                state.endDate = end.value;
                load(1);
            });
            view.querySelector('#decl-reset').addEventListener('click', function () {
                keyword.value = '';
                status.value = '';
                taxType.value = '';
                if (taxpayer) {
                    taxpayer.value = '';
                }
                start.value = '';
                end.value = '';
                state.keyword = '';
                state.status = '';
                state.taxTypeId = '';
                state.taxpayerId = '';
                state.startDate = '';
                state.endDate = '';
                load(1);
            });
            keyword.addEventListener('keydown', function (event) {
                if (event.key === 'Enter') {
                    view.querySelector('#decl-search').click();
                }
            });
            load(1);
        }
    };

    App.pages.audits = {
        title: '申报审核',
        crumb: '业务办理 / 申报审核',
        roles: ['ADMIN', 'AUDITOR'],
        render: function (view, user) {
            var state = { tab: 'pending', page: 1, size: 10, keyword: '', items: [], pendingTotal: 0 };

            view.innerHTML =
                '<div class="page-head"><div><h3>申报审核</h3><p>审核纳税人提交的申报资料，记录审核意见与流转过程。</p></div></div>' +
                '<div class="card" style="margin-bottom:16px"><div class="tabs"><button type="button" class="tab active" data-tab="pending">待审核申报 <span class="count-dot" id="pending-count">0</span></button><button type="button" class="tab" data-tab="records">审核记录</button></div></div>' +
                '<div class="card filter-bar"><div class="field grow"><label>关键词</label><input class="input" id="audit-keyword" placeholder="申报编号 / 纳税人 / 审核意见"></div>' +
                '<div class="filter-actions"><button type="button" class="btn primary" id="audit-search">' + App.icon('query', 15) + '查询</button><button type="button" class="btn ghost" id="audit-reset">' + App.icon('refresh', 15) + '重置</button></div></div>' +
                '<div class="card"><div class="table-wrap" id="audit-table">' + App.loading() + '</div><div id="audit-pager"></div></div>';

            var table = view.querySelector('#audit-table');
            var pager = view.querySelector('#audit-pager');
            var keyword = view.querySelector('#audit-keyword');
            var pendingCount = view.querySelector('#pending-count');

            function load(page) {
                state.page = page || state.page;
                table.innerHTML = App.loading();
                pager.innerHTML = '';
                var request = state.tab === 'pending'
                    ? API.get('/audits/pending' + App.query({ keyword: state.keyword, page: state.page, size: state.size }))
                    : API.get('/audits/records' + App.query({ keyword: state.keyword, page: state.page, size: state.size }));
                request.then(function (result) {
                    state.items = result.items || [];
                    if (state.tab === 'pending') {
                        state.pendingTotal = result.total || 0;
                        pendingCount.textContent = state.pendingTotal;
                    }
                    if (!state.items.length) {
                        table.innerHTML = state.tab === 'pending'
                            ? App.empty('暂无待审核申报', '纳税人提交申报后会出现在这里')
                            : App.empty('暂无审核记录', '审核、驳回与缴款操作都会记录在这里');
                        return;
                    }
                    if (state.tab === 'pending') {
                        renderPending(result);
                    } else {
                        renderRecords(result);
                    }
                }).catch(function (error) {
                    table.innerHTML = App.errorBox(error);
                });
            }

            function renderPending(result) {
                table.innerHTML = '<table class="data"><thead><tr><th>申报编号</th><th>纳税人</th><th>税种</th><th>所属期</th><th>计税金额</th><th>应纳税额</th><th>提交时间</th><th>操作</th></tr></thead><tbody>' +
                    state.items.map(function (item) {
                        return '<tr><td><span class="cell-main mono">' + App.esc(item.declarationNo) + '</span></td>' +
                            '<td><div class="cell-main">' + App.esc(item.taxpayerName) + '</div><div class="cell-sub mono">' + App.esc(item.taxpayerNo) + '</div></td>' +
                            '<td><div class="cell-main" style="font-weight:500">' + App.esc(item.taxTypeName) + '</div><div class="cell-sub">' + App.esc(item.taxTypeCode) + ' · ' + App.rate(item.taxRate) + '</div></td>' +
                            '<td><span class="cell-date">' + App.esc(App.dateOnly(item.periodStart)) + ' 至 ' + App.esc(App.dateOnly(item.periodEnd)) + '</span></td>' +
                            '<td><span class="cell-money">' + App.money(item.taxableAmount) + '</span></td>' +
                            '<td><span class="cell-money">' + App.money(item.taxAmount) + '</span></td>' +
                            '<td><span class="cell-date">' + App.esc(App.dateTime(item.submitTime)) + '</span></td>' +
                            '<td class="actions"><button type="button" class="btn link" data-view="' + item.id + '">详情</button><button type="button" class="btn link danger" data-reject="' + item.id + '">驳回</button><button type="button" class="btn link" style="color:#169c67" data-approve="' + item.id + '">通过</button></td></tr>';
                    }).join('') + '</tbody></table>';
                pager.innerHTML = App.pager(result);
                App.bindPager(view, result, load);
                table.querySelectorAll('[data-view]').forEach(function (button) {
                    button.addEventListener('click', function () {
                        showDeclarationDetail(Number(button.getAttribute('data-view')), user, function () {
                            load();
                        });
                    });
                });
                table.querySelectorAll('[data-approve]').forEach(function (button) {
                    button.addEventListener('click', function () {
                        var item = findItem(button.getAttribute('data-approve'));
                        reviewDeclaration(item.id, item.declarationNo, true, function () {
                            load();
                        });
                    });
                });
                table.querySelectorAll('[data-reject]').forEach(function (button) {
                    button.addEventListener('click', function () {
                        var item = findItem(button.getAttribute('data-reject'));
                        reviewDeclaration(item.id, item.declarationNo, false, function () {
                            load();
                        });
                    });
                });
            }

            function renderRecords(result) {
                table.innerHTML = '<table class="data"><thead><tr><th>时间</th><th>申报编号</th><th>动作</th><th>操作人</th><th>审核 / 处理意见</th><th>操作</th></tr></thead><tbody>' +
                    state.items.map(function (item) {
                        var cls = item.action === '通过' ? 'green' : (item.action === '驳回' ? 'red' : (item.action === '缴款' ? 'orange' : 'blue'));
                        return '<tr><td><span class="cell-date">' + App.esc(App.dateTime(item.createdAt)) + '</span></td>' +
                            '<td><span class="cell-main mono">' + App.esc(item.declarationNo) + '</span></td>' +
                            '<td><span class="badge ' + cls + '">' + App.esc(item.action) + '</span></td>' +
                            '<td>' + App.esc(item.auditor || '—') + '</td>' +
                            '<td class="muted" style="max-width:360px">' + App.esc(item.comment || '—') + '</td>' +
                            '<td class="actions"><button type="button" class="btn link" data-view="' + item.declarationId + '">查看申报</button></td></tr>';
                    }).join('') + '</tbody></table>';
                pager.innerHTML = App.pager(result);
                App.bindPager(view, result, load);
                table.querySelectorAll('[data-view]').forEach(function (button) {
                    button.addEventListener('click', function () {
                        showDeclarationDetail(Number(button.getAttribute('data-view')), user, function () {
                            load();
                        });
                    });
                });
            }

            function findItem(id) {
                return state.items.filter(function (item) {
                    return item.id === Number(id);
                })[0];
            }

            view.querySelectorAll('[data-tab]').forEach(function (button) {
                button.addEventListener('click', function () {
                    state.tab = button.getAttribute('data-tab');
                    state.page = 1;
                    view.querySelectorAll('[data-tab]').forEach(function (tab) {
                        tab.classList.toggle('active', tab === button);
                    });
                    load(1);
                });
            });
            view.querySelector('#audit-search').addEventListener('click', function () {
                state.keyword = keyword.value.trim();
                load(1);
            });
            view.querySelector('#audit-reset').addEventListener('click', function () {
                keyword.value = '';
                state.keyword = '';
                load(1);
            });
            keyword.addEventListener('keydown', function (event) {
                if (event.key === 'Enter') {
                    view.querySelector('#audit-search').click();
                }
            });
            load(1);
        }
    };

    App.actions = App.actions || {};
    App.actions.showDeclaration = function (id) {
        showDeclarationDetail(id, App.state.user, function () {
            App.renderRoute();
        });
    };
})();
