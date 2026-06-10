"""Punto de entrada ASGI del backend."""

from app.factory import create_app


app = create_app()
