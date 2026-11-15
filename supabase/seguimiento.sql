-- Ejecutar manualmente en Supabase SQL Editor.
-- Usa public.seguimientos del esquema existente y solo crea posiciones_usuario.

create table if not exists public.posiciones_usuario (
    usuario_id uuid primary key references public.usuarios(id) on delete cascade,
    latitud double precision not null check (latitud between -90 and 90),
    longitud double precision not null check (longitud between -180 and 180),
    precision_metros real check (precision_metros >= 0),
    actualizado_utc timestamptz not null default now()
);

-- Bases creadas con una versión anterior de este script usaban la columna "fecha".
do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'posiciones_usuario' and column_name = 'fecha'
    ) and not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'posiciones_usuario' and column_name = 'actualizado_utc'
    ) then
        alter table public.posiciones_usuario rename column fecha to actualizado_utc;
    end if;
end $$;

alter table public.posiciones_usuario enable row level security;
alter table public.seguimientos enable row level security;
alter table public.usuarios enable row level security;
alter table public.rutas enable row level security;
alter table public.nodos enable row level security;

-- Verifica la vinculación sin abrir la lectura de perfiles a otros usuarios.
create or replace function public.ecoguia_encargado_puede_ver_no_vidente(
    p_usuario_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.usuarios as u
        where u.id = p_usuario_id
          and u.rol = 'no_vidente'
          and u.encargado_id = (select auth.uid())
    );
$$;

revoke all on function public.ecoguia_encargado_puede_ver_no_vidente(uuid) from public;
grant execute on function public.ecoguia_encargado_puede_ver_no_vidente(uuid) to authenticated;

drop policy if exists usuarios_perfil_propio_y_asignados on public.usuarios;
create policy usuarios_perfil_propio_y_asignados
    on public.usuarios
    for select
    to authenticated
    using (
        id = (select auth.uid())
        or (
            rol = 'no_vidente'
            and encargado_id = (select auth.uid())
        )
    );
grant select on public.usuarios to authenticated;

drop policy if exists rutas_ver_propias_y_asignadas on public.rutas;
create policy rutas_ver_propias_y_asignadas
    on public.rutas
    for select
    to authenticated
    using (
        usuario_id = (select auth.uid())
        or creador_id = (select auth.uid())
        or public.ecoguia_encargado_puede_ver_no_vidente(usuario_id)
    );
grant select on public.rutas to authenticated;

drop policy if exists nodos_ver_de_rutas_propias_y_asignadas on public.nodos;
create policy nodos_ver_de_rutas_propias_y_asignadas
    on public.nodos
    for select
    to authenticated
    using (
        exists (
            select 1
            from public.rutas as r
            where r.id = nodos.ruta_id
              and (
                  r.usuario_id = (select auth.uid())
                  or r.creador_id = (select auth.uid())
                  or public.ecoguia_encargado_puede_ver_no_vidente(r.usuario_id)
              )
        )
    );
grant select on public.nodos to authenticated;

drop policy if exists no_vidente_insertar_su_ultima_posicion on public.posiciones_usuario;
create policy no_vidente_insertar_su_ultima_posicion
    on public.posiciones_usuario
    for insert
    to authenticated
    with check (usuario_id = (select auth.uid()));

drop policy if exists no_vidente_actualizar_su_ultima_posicion on public.posiciones_usuario;
create policy no_vidente_actualizar_su_ultima_posicion
    on public.posiciones_usuario
    for update
    to authenticated
    using (usuario_id = (select auth.uid()))
    with check (usuario_id = (select auth.uid()));

drop policy if exists usuario_leer_su_ultima_posicion on public.posiciones_usuario;
create policy usuario_leer_su_ultima_posicion
    on public.posiciones_usuario
    for select
    to authenticated
    using (usuario_id = (select auth.uid()));

drop policy if exists encargado_leer_ultima_posicion_asignada on public.posiciones_usuario;
create policy encargado_leer_ultima_posicion_asignada
    on public.posiciones_usuario
    for select
    to authenticated
    using (public.ecoguia_encargado_puede_ver_no_vidente(usuario_id));

grant select, insert, update on public.posiciones_usuario to authenticated;

drop policy if exists no_vidente_leer_sus_seguimientos on public.seguimientos;
create policy no_vidente_leer_sus_seguimientos
    on public.seguimientos
    for select
    to authenticated
    using (usuario_id = (select auth.uid()));

drop policy if exists no_vidente_insertar_su_seguimiento on public.seguimientos;
create policy no_vidente_insertar_su_seguimiento
    on public.seguimientos
    for insert
    to authenticated
    with check (usuario_id = (select auth.uid()));

drop policy if exists no_vidente_finalizar_su_seguimiento on public.seguimientos;
create policy no_vidente_finalizar_su_seguimiento
    on public.seguimientos
    for update
    to authenticated
    using (usuario_id = (select auth.uid()))
    with check (usuario_id = (select auth.uid()));

drop policy if exists encargado_leer_seguimientos_asignados on public.seguimientos;
create policy encargado_leer_seguimientos_asignados
    on public.seguimientos
    for select
    to authenticated
    using (public.ecoguia_encargado_puede_ver_no_vidente(usuario_id));

grant select, insert, update on public.seguimientos to authenticated;

-- Supabase Realtime no publica tablas automáticamente.
do $$
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        if not exists (
            select 1 from pg_publication_tables
            where pubname = 'supabase_realtime'
              and schemaname = 'public'
              and tablename = 'posiciones_usuario'
        ) then
            alter publication supabase_realtime add table public.posiciones_usuario;
        end if;
        if not exists (
            select 1 from pg_publication_tables
            where pubname = 'supabase_realtime'
              and schemaname = 'public'
              and tablename = 'seguimientos'
        ) then
            alter publication supabase_realtime add table public.seguimientos;
        end if;
    end if;
end;
$$;
