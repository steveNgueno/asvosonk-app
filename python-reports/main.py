#!/usr/bin/env python3
"""
ASVOSONK Report Generator — Entry Point

Usage:
    python main.py <type> <date_from> <date_to> <output_dir>

Types:
    session   — Report for a single meeting session
    monthly   — Full monthly cashbox statement
    quarterly — Quarterly aggregated report

Exit codes:
    0 — Success
    1 — Error (invalid arguments, generation failure, etc.)
"""

import sys
from datetime import date, datetime
from pathlib import Path

# Ensure the parent directory is on sys.path so that
# reports/ modules can import db_config
sys.path.insert(0, str(Path(__file__).resolve().parent))


def parse_date(s: str) -> date:
    """Parse an ISO-format date string (YYYY-MM-DD)."""
    return datetime.strptime(s, "%Y-%m-%d").date()


def main():
    if len(sys.argv) < 5:
        print("Usage: python main.py <type> <date_from> <date_to> <output_dir>", file=sys.stderr)
        sys.exit(1)

    report_type = sys.argv[1].lower()
    date_from = parse_date(sys.argv[2])
    date_to = parse_date(sys.argv[3])
    output_dir = Path(sys.argv[4])

    if report_type not in ("session", "monthly", "quarterly"):
        print(f"Type de rapport invalide: {report_type}. "
              f"Types autorisés: session, monthly, quarterly", file=sys.stderr)
        sys.exit(1)

    # Ensure output directory exists
    output_dir.mkdir(parents=True, exist_ok=True)

    # Dispatch to the correct module
    try:
        if report_type == "session":
            from reports.session_report import generate
        elif report_type == "monthly":
            from reports.monthly_report import generate
        elif report_type == "quarterly":
            from reports.quarterly_report import generate
        else:
            raise ValueError(f"Unknown report type: {report_type}")

        output_path = generate(date_from, date_to, output_dir)
        # Print the absolute path to stdout so the Java caller can read it
        print(output_path.resolve(), flush=True)
        print(f"Rapport généré : {output_path}", file=sys.stderr, flush=True)

    except Exception as e:
        print(f"ERREUR lors de la génération du rapport : {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
