from decimal import Decimal
from typing import Annotated

from pydantic import PlainSerializer

# Decimal exacto internamente, número (no string) en el JSON de respuesta
DecimalJson = Annotated[Decimal, PlainSerializer(float, return_type=float, when_used="json")]
