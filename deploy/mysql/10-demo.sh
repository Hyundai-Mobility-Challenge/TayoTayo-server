# Sourced by the official MySQL entrypoint before accepting network traffic.
# Reject malformed hashes before using them in an SQL string.
demo_hash_pattern='^pbkdf2\$[A-Za-z0-9+/]{22}==\$[A-Za-z0-9+/]{43}=$'
if [[ ! ${DEMO_ADMIN_HASH:-} =~ $demo_hash_pattern ]]; then
    echo 'DEMO_ADMIN_HASH must be a TayoTayo PBKDF2-SHA256 hash.' >&2
    exit 1
fi

# Only the local seed uses the published admin password and vehicle credential.
# Replace both before the INSERT statements reach MySQL.
sed -e "s@'pbkdf2[^']*'@'${DEMO_ADMIN_HASH}'@" \
    -e "s/SHA2('DEMO-HASAL-VEHICLE-1',256)/SHA2(RANDOM_BYTES(32),256)/" \
    /opt/tayotayo/seed.sql | docker_process_sql --default-character-set=utf8mb4
