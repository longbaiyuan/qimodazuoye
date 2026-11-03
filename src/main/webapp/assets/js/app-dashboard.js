(function () {
    'use strict';

    var App = window.App;

    function statCard(icon, color, label, value, hint) {
        return '<div class="stat-card"><div class="stat-icon ' + color + '">' + App.icon(icon, 19) + '</div>' +
            '<div class="stat-body"><div class="label">' + App.esc(label) + '</div><div class="value">' + value + '</div>' +
            (hint ? '<div class="hint">' + App.esc(hint) + '</div>' : '') + '</div></div>';
    }

    function monthlyChart(items) {
        var max = 1;
        (items || []).forEach(function (item) {
            max = Math.max(max, Number(item.amount || 0));
        });
        return '<div class="chart-box"><div class="chart-grid"></div><div class="bars">' + (items || []).map(function (item, index) {
            var amount = Number(item.amount || 0);
            var height = amount <= 0 ? 4 : Math.max(8, Math.round(amount / max * 145));
            var month = String(item.month || '').slice(5) + '月';
            return '<div class="bar-col"><span class="bar-amount">' + App.num(amount, 0) + '</span>' +
                '<div class="bar-fill' + (index === items.length - 1 ? ' current' : '') + '" style="height:' + height + 'px" title="' + App.esc(item.month + ' · ' + App.money(amount)) + '"></div>' +
                '<span class="bar-month">' + App.esc(month) + '</span></div>';
        }).join('') + '</div></div>';
    }

    function distribution(items) {
        if (!items || !items.length) {
            return App.empty('暂无税种数据', '申报通过后将生成分布统计');
        }
        var max = 1;
        items.forEach(function (item) {
            max = Math.max(max, Number(item.amount || 0));
        });
        return '<div class="dist-list">' + items.map(function (item) {
            var amount = Number(item.amount || 0);
            var percent = Math.max(2, Math.round(amount / max * 100));
            return '<div class="dist-item"><div class="dist-top"><span>' + App.esc(item.name) + '</span><span>' + App.money(amount) + '</span></div>' +
                '<div class="dist-track"><div class="dist-fill" style="width:' + percent + '%"></div></div></div>';
        }).join('') + '</div>';
    }

    function recentTable(items) {
        if (!items || !items.length) {
            return App.empty('暂无申报记录', '创建第一条纳税申报后这里会显示记录');
        }
        return '<div class="table-wrap"><table class="data" style="min-width:0"><thead><tr><th>申报编号</th><th>纳税人 / 税种</th><th>税额</th><th>状态</th></tr></thead><tbody>' +
            items.map(function (item) {
                return '<tr data-declaration="' + item.id + '" style="cursor:pointer"><td><div class="cell-main mono">' + App.esc(item.declarationNo) + '</div><div class="cell-sub">' + App.esc(App.dateOnly(item.createdAt)) + '</div></td>' +
                    '<td><div class="cell-main">' + App.esc(item.taxpayerName) + '</div><div class="cell-sub">' + App.esc(item.taxTypeName) + '</div></td>' +
                    '<td><span class="cell-money">' + App.money(item.taxAmount) + '</span></td>' +
                    '<td>' + App.declarationBadge(item.status) + '</td></tr>';
            }).join('') + '</tbody></table></div>';
    }

    function pendingList(items) {
        if (!items || !items.length) {
            return App.empty('暂无待办事项', '当前没有待审核的申报');
        }
        return '<div class="rank-list">' + items.map(function (item) {
            return '<div class="rank-item" data-declaration="' + item.id + '" style="cursor:pointer"><div class="rank-no">' + App.icon('declarations', 13) + '</div>' +
                '<div style="flex:1;min-width:0"><div class="rank-name">' + App.esc(item.taxpayerName) + '</div><div class="cell-sub">' + App.esc(item.declarationNo + ' · ' + item.taxTypeName) + '</div></div>' +
                '<span class="rank-amount">' + App.money(item.taxAmount) + '</span></div>';
        }).join('') + '</div>';
    }

    function rankList(items) {
        if (!items || !items.length) {
            return App.empty('暂无缴款数据', '缴款完成后将展示纳税人排名');
        }
        return '<div class="rank-list">' + items.map(function (item, index) {
            return '<div class="rank-item"><span class="rank-no">' + (index + 1) + '</span><span class="rank-name">' + App.esc(item.name) + '</span>' +
                '<span class="rank-amount">' + App.money(item.amount) + '</span></div>';
        }).join('') + '</div>';
    }

    App.pages.dashboard = {
        title: '工作台',
        crumb: '业务办理 / 工作台',
        render: function (view, user) {
            return API.get('/dashboard').then(function (data) {
                var isTaxpayer = user.role === 'TAXPAYER';
                var greeting = isTaxpayer ? '您好，' + (user.displayName || user.username) : '欢迎回来，' + (user.displayName || user.username);
                var scope = isTaxpayer ? (user.taxpayerName || '当前纳税人') : '全局征管数据';
                var actions = isTaxpayer
                    ? '<button class="btn primary" data-go="declarations">' + App.icon('plus', 15) + '新增申报</button><button class="btn ghost" data-go="payments">' + App.icon('card', 15) + '去缴款</button>'
                    : '<button class="btn primary" data-go="declarations">' + App.icon('plus', 15) + '新增申报</button><button class="btn ghost" data-go="audits">' + App.icon('audit', 15) + '待办审核</button>';

                view.innerHTML =
                    '<div class="greeting"><h3>' + App.esc(greeting) + '</h3>' +
                    '<p>今天是 ' + App.esc(App.today()) + '，以下是' + App.esc(scope) + '的最新业务概览。</p>' +
                    '<div class="greeting-tags"><span class="g-tag">' + App.esc(App.roleLabel(user.role)) + '</span>' +
                    (user.taxpayerName ? '<span class="g-tag">' + App.esc(user.taxpayerName) + '</span>' : '') +
                    '<span class="g-tag">待审核 ' + Number(data.pendingCount || 0) + ' 笔</span></div>' +
                    '<div class="page-actions" style="margin-top:16px;position:relative;z-index:2">' + actions + '</div></div>' +
                    '<div class="stat-grid">' +
                    statCard('taxpayers', 'blue', isTaxpayer ? '纳税人档案' : '正常纳税人', App.num(data.taxpayerCount, 0), isTaxpayer ? '当前绑定主体' : '状态为正常的纳税人') +
                    statCard('declarations', 'navy', '申报总数', App.num(data.declarationCount, 0), '累计纳税申报记录') +
                    statCard('clock', 'orange', '待审核', App.num(data.pendingCount, 0), '等待审核处理') +
                    statCard('check', 'green', '已通过', App.num(data.approvedCount, 0), '尚未缴款的通过记录') +
                    statCard('payment', 'purple', '累计缴款', App.money(data.paidTotal), '已入库税款合计') +
                    statCard('card', 'red', '待缴税额', App.money(data.unpaidAmount), '已通过未缴款金额') +
                    '</div>' +
                    '<div class="dash-grid"><div class="card"><div class="card-head"><h4>近六个月税款趋势</h4><span class="card-sub">单位：元</span></div>' + monthlyChart(data.monthlyTax) + '</div>' +
                    '<div class="card"><div class="card-head"><h4>税种税额分布</h4><span class="card-sub">已通过 / 已缴款</span></div><div style="padding:0 20px 20px">' + distribution(data.taxTypeDistribution) + '</div></div></div>' +
                    '<div class="dash-grid equal"><div class="card"><div class="card-head"><h4>最近申报</h4><button class="btn link" data-go="declarations">查看全部</button></div>' + recentTable(data.recentDeclarations) + '</div>' +
                    '<div class="card"><div class="card-head"><h4>' + (isTaxpayer ? '我的待审核申报' : '待审核申报') + '</h4><button class="btn link" data-go="' + (isTaxpayer ? 'declarations' : 'audits') + '">进入处理</button></div><div style="padding:4px 18px 12px">' + pendingList(data.pendingDeclarations) + '</div></div></div>' +
                    '<div class="dash-grid equal"><div class="card"><div class="card-head"><h4>纳税人缴款排行</h4><span class="card-sub">按累计缴款金额</span></div><div style="padding:4px 18px 12px">' + rankList(data.topTaxpayers) + '</div></div>' +
                    '<div class="card"><div class="card-head"><h4>办税提醒</h4><span class="card-sub">业务提示</span></div><div class="card-pad" style="padding-top:0">' +
                    '<div class="status-chips"><span class="status-chip">发票数量 <strong>' + App.num(data.invoiceCount, 0) + '</strong></span><span class="status-chip">开票金额 <strong>' + App.money(data.invoiceAmount) + '</strong></span></div>' +
                    '<div class="info-banner" style="margin-bottom:10px">' + App.icon('info', 17) + '<span>待审核申报请及时处理，审核通过后纳税人即可在线缴款。</span></div>' +
                    '<div class="info-banner" style="margin-bottom:0">' + App.icon('info', 17) + '<span>申报数据提交后仍可在审核前查看详情，审核通过后不可修改。</span></div>' +
                    '</div></div></div>';

                view.querySelectorAll('[data-go]').forEach(function (button) {
                    button.addEventListener('click', function () {
                        App.go(button.getAttribute('data-go'));
                    });
                });
                view.querySelectorAll('[data-declaration]').forEach(function (row) {
                    row.addEventListener('click', function () {
                        var id = Number(row.getAttribute('data-declaration'));
                        if (App.actions && App.actions.showDeclaration) {
                            App.actions.showDeclaration(id);
                        } else {
                            App.go('declarations');
                        }
                    });
                });
            });
        }
    };
})();
