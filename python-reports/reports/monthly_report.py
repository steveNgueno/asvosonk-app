"""
Monthly report — full cashbox statement for a month.
Covers all sessions, cashbox movements, sanctions, and revolving fund activity.
"""

from datetime import date
from pathlib import Path
from jinja2 import Template
import weasyprint

from db_config import execute_query


# ── HTML Template ───────────────────────────────────────────

MONTHLY_TEMPLATE = Template("""
<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <title>État mensuel — {{ from_date }} au {{ to_date }}</title>
    <style>
        @page { size: A4; margin: 2cm; }
        body { font-family: 'DejaVu Sans', sans-serif; font-size: 11pt; color: #333; }
        h1 { font-size: 16pt; color: #1a3a5c; border-bottom: 2px solid #1a3a5c; }
        h2 { font-size: 13pt; color: #2a5a8c; margin-top: 20px; }
        table { width: 100%; border-collapse: collapse; margin: 10px 0; font-size: 10pt; }
        th { background-color: #1a3a5c; color: white; padding: 6px 8px; text-align: left; }
        td { padding: 5px 8px; border-bottom: 1px solid #ddd; }
        .text-right { text-align: right; }
        .text-center { text-align: center; }
        .positive { color: #155724; }
        .negative { color: #721c24; }
        .summary-box { background: #f8f9fa; padding: 12px; border-radius: 5px; margin: 10px 0; }
        .footer { margin-top: 30px; font-size: 9pt; color: #999; text-align: center; }
    </style>
</head>
<body>
    <h1>État mensuel des caisses</h1>
    <p>Période du <strong>{{ from_date }}</strong> au <strong>{{ to_date }}</strong></p>

    <h2>Séances du mois</h2>
    <table>
        <thead>
            <tr>
                <th>Date</th>
                <th>Statut</th>
                <th>Bénéficiaire</th>
                <th class="text-right">Participants</th>
            </tr>
        </thead>
        <tbody>
            {% for s in sessions %}
            <tr>
                <td>{{ s.session_date }}</td>
                <td>{{ s.status }}</td>
                <td>{{ s.beneficiary_name or '—' }}</td>
                <td class="text-right">{{ s.participant_count or 0 }}</td>
            </tr>
            {% endfor %}
        </tbody>
    </table>

    <h2>Mouvements par caisse</h2>
    {% for cashbox in cashbox_summary %}
    <h3>{{ cashbox.type }}</h3>
    <div class="summary-box">
        <div class="row"><strong>Total entrées :</strong> {{ "{:,.0f}".format(cashbox.total_in) }} FCFA</div>
        <div class="row"><strong>Total sorties :</strong> {{ "{:,.0f}".format(cashbox.total_out) }} FCFA</div>
        <div class="row"><strong>Solde fin de mois :</strong> {{ "{:,.0f}".format(cashbox.closing_balance) }} FCFA</div>
    </div>
    {% endfor %}

    <h2>Sanctions du mois</h2>
    <table>
        <thead>
            <tr>
                <th>Membre</th>
                <th>Date</th>
                <th class="text-right">Montant</th>
                <th>Motif</th>
                <th>Statut</th>
            </tr>
        </thead>
        <tbody>
            {% for s in sanctions %}
            <tr>
                <td>{{ s.full_name }}</td>
                <td>{{ s.sanction_date }}</td>
                <td class="text-right">{{ "{:,.0f}".format(s.amount) }} FCFA</td>
                <td>{{ s.reason }}</td>
                <td>{{ s.status }}</td>
            </tr>
            {% endfor %}
        </tbody>
    </table>

    <h2>Fonds de roulement</h2>
    {% if revolving_fund %}
    <div class="summary-box">
        <div class="row"><strong>Avances :</strong> {{ "{:,.0f}".format(revolving_fund.total_advances) }} FCFA</div>
        <div class="row"><strong>Remboursements :</strong> {{ "{:,.0f}".format(revolving_fund.total_repayments) }} FCFA</div>
        <div class="row"><strong>Défauts :</strong> {{ "{:,.0f}".format(revolving_fund.total_defaults) }} FCFA</div>
    </div>
    {% else %}
    <p>Aucun mouvement de fonds de roulement.</p>
    {% endif %}

    <div class="footer">
        ASVOSONK — Nkou-Assi — Rapport généré le {{ generation_date }}
    </div>
</body>
</html>
""")


def generate(from_date: date, to_date: date, output_dir: Path) -> Path:
    """
    Generate a monthly cashbox statement PDF.
    """

    sessions = execute_query("""
        SELECT
            ms.session_date::TEXT,
            ms.status,
            m.full_name AS beneficiary_name,
            (SELECT COUNT(*) FROM session_attendance WHERE session_id = ms.id) AS participant_count
        FROM meeting_session ms
        LEFT JOIN member m ON m.id = ms.beneficiary_id
        WHERE ms.session_date BETWEEN %s AND %s
        ORDER BY ms.session_date ASC
    """, (from_date, to_date))

    cashbox_summary = execute_query("""
        SELECT
            c.type,
            COALESCE(SUM(cm.amount) FILTER (WHERE cm.direction = 'in'), 0) AS total_in,
            COALESCE(SUM(cm.amount) FILTER (WHERE cm.direction = 'out'), 0) AS total_out,
            c.balance AS closing_balance
        FROM cashbox c
        LEFT JOIN cashbox_movement cm ON cm.cashbox_id = c.id
            AND cm.movement_date::DATE BETWEEN %s AND %s
        GROUP BY c.id, c.type, c.balance
        ORDER BY c.id ASC
    """, (from_date, to_date))

    sanctions = execute_query("""
        SELECT
            m.full_name,
            s.sanction_date::TEXT,
            s.amount,
            s.reason,
            s.status
        FROM sanction s
        JOIN member m ON m.id = s.member_id
        WHERE s.sanction_date BETWEEN %s AND %s
        ORDER BY s.sanction_date ASC
    """, (from_date, to_date))

    revolving_fund = execute_query("""
        SELECT
            COALESCE(SUM(amount) FILTER (WHERE movement_type = 'advance'), 0) AS total_advances,
            COALESCE(SUM(amount) FILTER (WHERE movement_type = 'repayment'), 0) AS total_repayments,
            COALESCE(SUM(amount) FILTER (WHERE movement_type = 'advance' AND NOT is_recovered), 0) AS total_defaults
        FROM revolving_fund_movement
        WHERE created_at::DATE BETWEEN %s AND %s
    """, (from_date, to_date))

    html = MONTHLY_TEMPLATE.render(
        from_date=from_date.isoformat(),
        to_date=to_date.isoformat(),
        sessions=sessions,
        cashbox_summary=cashbox_summary,
        sanctions=sanctions,
        revolving_fund=revolving_fund[0] if revolving_fund else None,
        generation_date=date.today().isoformat(),
    )

    output_path = output_dir / f"rapport_monthly_{from_date.isoformat()}_to_{to_date.isoformat()}.pdf"
    weasyprint.HTML(string=html).write_pdf(str(output_path))
    return output_path
