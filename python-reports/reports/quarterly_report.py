"""
Quarterly report — aggregation of 3 monthly cashbox statements.
Provides an overview of income, expenses, and balance trends.
"""

from datetime import date
from pathlib import Path
from jinja2 import Template
import weasyprint

from db_config import execute_query


# ── HTML Template ───────────────────────────────────────────

QUARTERLY_TEMPLATE = Template("""
<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <title>Rapport trimestriel — {{ from_date }} au {{ to_date }}</title>
    <style>
        @page { size: A4; margin: 2cm; }
        body { font-family: 'DejaVu Sans', sans-serif; font-size: 11pt; color: #333; }
        h1 { font-size: 16pt; color: #1a3a5c; border-bottom: 2px solid #1a3a5c; }
        h2 { font-size: 13pt; color: #2a5a8c; margin-top: 20px; }
        table { width: 100%; border-collapse: collapse; margin: 10px 0; font-size: 10pt; }
        th { background-color: #1a3a5c; color: white; padding: 6px 8px; text-align: left; }
        td { padding: 5px 8px; border-bottom: 1px solid #ddd; }
        .text-right { text-align: right; }
        .positive { color: #155724; }
        .negative { color: #721c24; }
        .summary-box { background: #f8f9fa; padding: 12px; border-radius: 5px; margin: 10px 0; }
        .month-row { display: flex; justify-content: space-between; padding: 4px 0; border-bottom: 1px solid #eee; }
        .footer { margin-top: 30px; font-size: 9pt; color: #999; text-align: center; }
    </style>
</head>
<body>
    <h1>Rapport trimestriel</h1>
    <p>Période du <strong>{{ from_date }}</strong> au <strong>{{ to_date }}</strong></p>

    <h2>Vue d'ensemble — Total des caisses</h2>
    <div class="summary-box">
        <div class="month-row"><strong>Total entrées :</strong> <span>{{ "{:,.0f}".format(total_in) }} FCFA</span></div>
        <div class="month-row"><strong>Total sorties :</strong> <span>{{ "{:,.0f}".format(total_out) }} FCFA</span></div>
        <div class="month-row"><strong>Solde net :</strong>
            <span class="{{ 'positive' if net_solde >= 0 else 'negative' }}">
                {{ "{:,.0f}".format(net_solde) }} FCFA
            </span>
        </div>
    </div>

    <h2>Détail par caisse</h2>
    <table>
        <thead>
            <tr>
                <th>Caisse</th>
                <th class="text-right">Entrées</th>
                <th class="text-right">Sorties</th>
                <th class="text-right">Solde</th>
            </tr>
        </thead>
        <tbody>
            {% for c in cashbox_totals %}
            <tr>
                <td>{{ c.type }}</td>
                <td class="text-right positive">{{ "{:,.0f}".format(c.total_in) }} FCFA</td>
                <td class="text-right negative">{{ "{:,.0f}".format(c.total_out) }} FCFA</td>
                <td class="text-right">{{ "{:,.0f}".format(c.balance) }} FCFA</td>
            </tr>
            {% endfor %}
        </tbody>
    </table>

    <h2>Activités du trimestre</h2>
    <div class="summary-box">
        <div class="month-row"><strong>Séances tenues :</strong> <span>{{ session_count }}</span></div>
        <div class="month-row"><strong>Sanctions créées :</strong> <span>{{ sanction_count }}</span></div>
        <div class="month-row"><strong>Total sanctions payées :</strong> <span>{{ "{:,.0f}".format(sanction_paid_total) }} FCFA</span></div>
    </div>

    <div class="footer">
        ASVOSONK — Nkou-Assi — Rapport généré le {{ generation_date }}
    </div>
</body>
</html>
""")


def generate(from_date: date, to_date: date, output_dir: Path) -> Path:
    """
    Generate a quarterly report PDF aggregating 3 months.
    """

    cashbox_totals = execute_query("""
        SELECT
            c.type,
            COALESCE(SUM(cm.amount) FILTER (WHERE cm.direction = 'in'), 0) AS total_in,
            COALESCE(SUM(cm.amount) FILTER (WHERE cm.direction = 'out'), 0) AS total_out,
            c.balance
        FROM cashbox c
        LEFT JOIN cashbox_movement cm ON cm.cashbox_id = c.id
            AND cm.movement_date::DATE BETWEEN %s AND %s
        GROUP BY c.id, c.type, c.balance
        ORDER BY c.id ASC
    """, (from_date, to_date))

    total_in = sum(c["total_in"] for c in cashbox_totals)
    total_out = sum(c["total_out"] for c in cashbox_totals)
    net_solde = total_in - total_out

    session_count = execute_query("""
        SELECT COUNT(*) AS count
        FROM meeting_session
        WHERE session_date BETWEEN %s AND %s
    """, (from_date, to_date))[0]["count"]

    sanctions = execute_query("""
        SELECT
            COUNT(*) AS count,
            COALESCE(SUM(amount) FILTER (WHERE status = 'paid'), 0) AS paid_total
        FROM sanction
        WHERE sanction_date BETWEEN %s AND %s
    """, (from_date, to_date))

    html = QUARTERLY_TEMPLATE.render(
        from_date=from_date.isoformat(),
        to_date=to_date.isoformat(),
        cashbox_totals=cashbox_totals,
        total_in=total_in,
        total_out=total_out,
        net_solde=net_solde,
        session_count=session_count,
        sanction_count=sanctions[0]["count"] if sanctions else 0,
        sanction_paid_total=sanctions[0]["paid_total"] if sanctions else 0,
        generation_date=date.today().isoformat(),
    )

    output_path = output_dir / f"rapport_quarterly_{from_date.isoformat()}_to_{to_date.isoformat()}.pdf"
    weasyprint.HTML(string=html).write_pdf(str(output_path))
    return output_path
