FROM mysql:8.4
COPY ["TayoTayo_실행용_회의반영.sql", "/opt/tayotayo/seed.sql"]
COPY --chmod=0444 deploy/mysql/10-demo.sh /docker-entrypoint-initdb.d/10-demo.sh
