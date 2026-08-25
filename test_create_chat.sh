#!/bin/bash
TOKEN=$(docker exec saanjha_db_node psql -U saanjha_user -d saanjha_auth -t -c "SELECT token FROM auth_tokens WHERE user_id = (SELECT id FROM auth_users WHERE email = 'rahul@example.com') LIMIT 1;" | tr -d ' ')
echo "Token: $TOKEN"
curl -s -X POST http://localhost:8080/api/v1/chats/conversations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"DIRECT_MESSAGE", "memberUserIds":["177e4cdb-3023-4df4-aeb1-b510cba7eb48"]}'
