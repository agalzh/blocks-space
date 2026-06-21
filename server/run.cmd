@echo off
REM Boot Paper in Docker (workaround for Windows 24H2 AF_UNIX regression).
REM First run pulls the image (~600 MB) and downloads Paper 1.20.4.
pushd "%~dp0\.."
docker compose up -d
echo.
echo Server starting. Tail logs with:    docker compose logs -f mc
echo Stop the server with:               docker compose down
echo Connect from Minecraft 1.20.4 to:   localhost:25565
popd
