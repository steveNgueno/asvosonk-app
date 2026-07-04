"""
Session report — details for a single meeting session.
Generates a PDF with attendance, tontine, beverage, and cashbox details.
"""

from datetime import date
from pathlib import Path
from jinja2 import Template
import weasyprint

from db_config import execute_query


# ── HTML Template ───────────────────────────────────────────

SESSION_TEMPLATE = Template("""
<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <title>Rapport de séance — {{ session_date }}</title>
    <style>
        @page { size: A4; margin: 2cm; }
        body { font-family: 'DejaVu Sans', sans-serif; font-size: 11pt; color: #333; }
        h1 { font-size: 16pt; color: #1a3a5c; border-bottom: 2px solid #1a3a5c; padding-bottom: 5px; }
        h2 { font-size: 13pt; color: #2a5a8c; margin-top: 20px; }
        table { width: 100%; border-collapse: collapse; margin: 10px 0; font-size: 10pt; }
        th { background-color: #1a3a5c; color: white; padding: 6px 8px; text-align: left; }
        td { padding: 5px 8px; border-bottom: 1px solid #ddd; }
        .text-right { text-align: right; }
        .text-center { text-align: center; }
        .badge-present { color: #155724; background: #d4edda; padding: 2px 6px; border-radius: 3px; }
        .badge-absent { color: #721c24; background: #f8d7da; padding: 2px 6px; border-radius: 3px; }
        .summary-box { background: #f8f9fa; padding: 12px; border-radius: 5px; margin: 10px 0; }
        .summary-row { display: flex; justify-content: space-between; margin: 4px 0; }
        .footer { margin-top: 30px; font-size: 9pt; color: #999; text-align: center; }
    </style>
</head>
<body>
    <h1>Rapport de séance — {{ session_date }}</h1>

    {% if session %}
    <div class="summary-box">
        <div class="summary-row"><strong>Date :</strong> <span>{{ session.session_date }}</span></div>
        <div class="summary-row"><strong>Statut :</strong> <span>{{ session.status }}</span></div>
        <div class="summary-row"><strong>Ordre du jour :</strong> <span>{{ session.agenda or '—' }}</span></div>
        <div class="summary-row"><strong>Bénéficiaire du jour :</strong> <span>{{ session.beneficiary_name or '—' }}</span></div>
    </div>
    {% endif %}

    <h2>Présences</h2>
    <table>
        <thead>
            <tr>
                <th>Membre</th>
                <th class="text-center">Présent</th>
                <th class="text-right">Montant payé</th>
                <th>Statut</th>
            </tr>
        </thead>
        <tbody>
            {% for att in attendances %}
            <tr>
                <td>{{ att.full_name }}</td>
                <td class="text-center">
                    {% if att.is_present %}
                    <span class="badge-present">Oui</span>
                    {% else %}
                    <span class="badge-absent">Non</span>
                    {% endif %}
                </td>
                <td class="text-right">{{ "{:,.0f}".format(att.amount_paid) }} FCFA</td>
                <td>{{ att.attendance_status }}</td>
            </tr>
            {% endfor %}
        </tbody>
    </table>

    <h2>Tontine du jour</h2>
    {% if tontine %}
    <div class="summary-box">
        <div class="summary-row"><strong>Bénéficiaire :</strong> <span>{{ tontine.beneficiary_name }}</span></div>
        <div class="summary-row"><strong>Montant brut :</strong> <span>{{ "{:,.0f}".format(tontine.gross_amount) }} FCFA</span></div>
        <div class="summary-row"><strong>Déductions (sanctions) :</strong> <span>{{ "{:,.0f}".format(tontine.sanction_deductions) }} FCFA</span></div>
        <div class="summary-row"><strong>Montant net :</strong> <span>{{ "{:,.0f}".format(tontine.net_amount) }} FCFA</span></div>
    </div>
    {% else %}
    <p>Aucune donnée de tontine disponible.</p>
    {% endif %}

    <h2>Mouvements de caisses</h2>
    <table>
        <thead>
            <tr>
                <th>Caisse</th>
                <th>Sens</th>
                <th class="text-right">Montant</th>
                <th>Motif</th>
            </tr>
        </thead>
        <tbody>
            {% for mov in movements %}
            <tr>
                <td>{{ mov.cashbox_type }}</td>
                <td>{{ '+' if mov.direction == 'in' else '-' }}</td>
                <td class="text-right">{{ "{:,.0f}".format(mov.amount) }} FCFA</td>
                <td>{{ mov.reason or '—' }}</td>
            </tr>
            {% endfor %}
        </tbody>
    </table>

    <div class="footer">
        ASVOSONK — Nkou-Assi — Rapport généré le {{ generation_date }}
    </div>
</body>
</html>
""")


def generate(from_date: date, to_date: date, output_dir: Path) -> Path:
    """
    Generate a session report PDF.
    """

    session = execute_query("""
        SELECT
            ms.session_date::TEXT,
            ms.status,
            ms.agenda,
            m.full_name AS beneficiary_name
        FROM meeting_session ms
        LEFT JOIN member m ON m.id = ms.beneficiary_id
        WHERE ms.session_date = %s
    """, (from_date,))
    session_data = session[0] if session else None

    attendances = execute_query("""
        SELECT
            m.full_name,
            sa.is_present,
            sa.amount_paid,
            sa.attendance_status
        FROM session_attendance sa
        JOIN member m ON m.id = sa.member_id
        JOIN meeting_session ms ON ms.id = sa.session_id
        WHERE ms.session_date = %s
        ORDER BY m.full_name ASC
    """, (from_date,))

    tontine = execute_query("""
        SELECT
            m.full_name AS beneficiary_name,
            COALESCE(SUM(sa.amount_paid), 0) AS gross_amount,
            0 AS sanction_deductions,
            COALESCE(SUM(sa.amount_paid), 0) AS net_amount
        FROM meeting_session ms
        JOIN session_attendance sa ON sa.session_id = ms.id
        LEFT JOIN member m ON m.id = ms.beneficiary_id
        WHERE ms.session_date = %s
        GROUP BY m.full_name
    """, (from_date,))
    tontine_data = tontine[0] if tontine else None

    movements = execute_query("""
        SELECT
            c.type AS cashbox_type,
            cm.direction,
            cm.amount,
            cm.reason
        FROM cashbox_movement cm
        JOIN cashbox c ON c.id = cm.cashbox_id
        JOIN meeting_session ms ON ms.id = cm.session_id
        WHERE ms.session_date = %s
        ORDER BY cm.movement_date ASC
    """, (from_date,))

    html = SESSION_TEMPLATE.render(
        session_date=from_date.isoformat(),
        session=session_data,
        attendances=attendances,
        tontine=tontine_data,
        movements=movements,
        generation_date=date.today().isoformat(),
    )

    output_path = output_dir / f"rapport_session_{from_date.isoformat()}.pdf"
    weasyprint.HTML(string=html).write_pdf(str(output_path))
    return output_path
