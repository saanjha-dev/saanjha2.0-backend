const { Pool } = require('pg');
const pool = new Pool({ connectionString: 'postgres://saanjha_user:postgres@localhost:5432/saanjha_auth' });

async function run() {
    const res = await pool.query("SELECT id, handle FROM usr.usr_profiles LIMIT 2;");
    console.log(res.rows);
    pool.end();
}
run();
