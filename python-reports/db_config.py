"""
Database configuration for the ASVOSONK report generator.

Connects to PostgreSQL in read-only mode using the dedicated
'asvosonk_reports' role. Parameters are read from environment
variables or use defaults for local development.
"""

import os
import psycopg2
from psycopg2 import sql

# ── Configuration ────────────────────────────────────────────
DB_HOST = os.environ.get("ASVOSONK_DB_HOST", "localhost")
DB_PORT = int(os.environ.get("ASVOSONK_DB_PORT", "5433"))
DB_NAME = os.environ.get("ASVOSONK_DB_NAME", "asvosonk")
DB_USER = os.environ.get("ASVOSONK_DB_USER", "asvosonk_reports")
DB_PASS = os.environ.get("ASVOSONK_DB_PASSWORD", "reports_pwd_change_me")


def get_connection():
    """
    Create and return a read-only PostgreSQL connection.
    The connection uses the 'asvosonk_reports' role which has
    SELECT-only access to all tables.
    """
    conn = psycopg2.connect(
        host=DB_HOST,
        port=DB_PORT,
        dbname=DB_NAME,
        user=DB_USER,
        password=DB_PASS,
        options="-c default_transaction_read_only=on",
    )
    conn.set_session(readonly=True, autocommit=True)
    return conn


def execute_query(query: str, params: tuple = None) -> list[dict]:
    """
    Execute a SELECT query and return results as a list of dictionaries.
    """
    conn = get_connection()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(query, params)
            return cur.fetchall()
    finally:
        conn.close()
