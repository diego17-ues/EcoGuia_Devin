-- Ejecutar en Supabase SQL Editor después de seguimiento.sql.
-- Aísla las rutas de cada persona no vidente y conserva la lectura del encargado vinculado.

begin;

-- Las rutas nuevas quedan asignadas al usuario autenticado que las crea.
-- La función RPC ecoguia_finalizar_ruta puede insertar rutas sin especificar usuario_id;
-- este trigger cubre ese caso. Más abajo solo se recuperan registros históricos cuyo creador
-- tiene un perfil de persona no vidente; el resto no se reasigna por falta de certeza.
create or replace function public.ecoguia_asignar_propietario_ruta()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
    usuario_actual uuid := (select auth.uid());
begin
    if usuario_actual is null then
        if new.usuario_id is null then
            raise exception 'La ruta debe tener un usuario propietario.';
        end if;
        return new;
    end if;

    if new.usuario_id is null then
        new.usuario_id := usuario_actual;
    elsif new.usuario_id <> usuario_actual then
        raise exception 'No puedes crear una ruta para otra cuenta.';
    end if;

    return new;
end;
$$;

drop trigger if exists rutas_asignar_propietario on public.rutas;
create trigger rutas_asignar_propietario
    before insert on public.rutas
    for each row
    execute function public.ecoguia_asignar_propietario_ruta();

-- Recupera el propietario de rutas antiguas solo cuando su creador es una persona no vidente.
-- Los demás registros sin usuario_id permanecen sin reasignar para no atribuirlos a otra cuenta.
update public.rutas as r
set usuario_id = r.creador_id
where r.usuario_id is null
  and r.creador_id is not null
  and exists (
      select 1
      from public.usuarios as u
      where u.id = r.creador_id
        and u.rol = 'no_vidente'
  );

-- Reemplaza la política de lectura de seguimiento.sql con el propietario real.
drop policy if exists rutas_ver_propias_y_asignadas on public.rutas;
create policy rutas_ver_propias_y_asignadas
    on public.rutas
    for select
    to authenticated
    using (
        usuario_id = (select auth.uid())
        or public.ecoguia_encargado_puede_ver_no_vidente(usuario_id)
    );

-- Las políticas permisivas se combinan con OR. Esta política restrictiva evita que
-- una política antigua más amplia vuelva a exponer rutas de otras cuentas.
drop policy if exists rutas_aislar_por_propietario on public.rutas;
create policy rutas_aislar_por_propietario
    on public.rutas
    as restrictive
    for select
    to authenticated
    using (
        usuario_id = (select auth.uid())
        or public.ecoguia_encargado_puede_ver_no_vidente(usuario_id)
    );

drop policy if exists nodos_aislar_por_propietario_ruta on public.nodos;
create policy nodos_aislar_por_propietario_ruta
    on public.nodos
    as restrictive
    for select
    to authenticated
    using (
        exists (
            select 1
            from public.rutas as r
            where r.id = nodos.ruta_id
              and (
                  r.usuario_id = (select auth.uid())
                  or public.ecoguia_encargado_puede_ver_no_vidente(r.usuario_id)
              )
        )
    );

grant select on public.rutas, public.nodos to authenticated;

commit;
