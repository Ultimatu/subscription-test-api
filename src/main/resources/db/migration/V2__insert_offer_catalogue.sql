insert into offer (code, label, technology, monthly_price, active, version, created_at, updated_at, created_by, updated_by)
select code, label, technology, monthly_price, true, 0, now(), now(), 'system', 'system'
from (values
    ('FTTH-30',   'Fibre Essentiel 30 Mbps',     'FTTH',      15000),
    ('FTTH-100',  'Fibre Confort 100 Mbps',      'FTTH',      25000),
    ('FTTH-200',  'Fibre Premium 200 Mbps',      'FTTH',      40000),
    ('FTTH-500',  'Fibre Business 500 Mbps',     'FTTH',      75000),
    ('FTTH-1000', 'Fibre Entreprise 1 Gbps',     'FTTH',      150000),
    ('SAT-20',    'Satellite Decouverte 20 Mbps', 'SATELLITE', 35000),
    ('SAT-50',    'Satellite Regions 50 Mbps',   'SATELLITE', 60000),
    ('SAT-100',   'Satellite Pro 100 Mbps',      'SATELLITE', 110000),
    ('BOX4G-50',  'Box 4G Nomade 50 Go',         '4G_BOX',    12000),
    ('BOX4G-150', 'Box 4G Famille 150 Go',       '4G_BOX',    22000),
    ('BOX4G-UNL', 'Box 4G Illimite',             '4G_BOX',    45000)
) as catalogue (code, label, technology, monthly_price);
