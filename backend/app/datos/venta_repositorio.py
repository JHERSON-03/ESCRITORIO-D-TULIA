from typing import Any

from psycopg.types.json import Jsonb

from app.core.database import obtener_conexion


class VentaRepositorio:
    def registrar(
        self,
        *,
        empleado_id: int,
        sede_id: int,
        tipo_comprobante_id: int,
        metodo_pago_id: int,
        items: list[dict[str, int]],
        cliente_id: int | None,
        canal: str,
    ) -> dict[str, Any]:
        """Una sola llamada atómica: pedido + detalle + comprobante. El trigger
        tr_descontar_stock_comprobante descuenta el stock en la misma transacción."""
        with obtener_conexion() as conn:
            return conn.execute(
                """
                select c.comprobante_id                 as id,
                       c.comprobante_pedido_id          as pedido_id,
                       t.tipo_comprobante_nombre        as tipo_comprobante,
                       c.comprobante_serie              as serie,
                       c.comprobante_numero_correlativo as numero,
                       c.comprobante_fecha              as fecha,
                       c.comprobante_total              as total
                  from public.fn_registrar_venta(
                           p_empleado_id         => %(empleado_id)s,
                           p_sede_id             => %(sede_id)s,
                           p_tipo_comprobante_id => %(tipo_comprobante_id)s,
                           p_metodo_pago_id      => %(metodo_pago_id)s,
                           p_items               => %(items)s,
                           p_cliente_id          => %(cliente_id)s,
                           p_canal               => %(canal)s) c
                  join public.tipo_comprobantes t on t.tipo_comprobante_id = c.comprobante_tipo_id
                """,
                {
                    "empleado_id": empleado_id,
                    "sede_id": sede_id,
                    "tipo_comprobante_id": tipo_comprobante_id,
                    "metodo_pago_id": metodo_pago_id,
                    "items": Jsonb(items),
                    "cliente_id": cliente_id,
                    "canal": canal,
                },
            ).fetchone()
