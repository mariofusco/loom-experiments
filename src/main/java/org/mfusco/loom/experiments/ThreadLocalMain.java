package org.mfusco.loom.experiments;

public class ThreadLocalMain {

    public static void main(String[] args) {
        var server = new Server();

        Thread authorized = new Thread(() -> callServer(server, true), "Authorized");
        Thread notAuthorized = new Thread(() -> callServer(server, false), "NOT Authorized");

        authorized.start();
        notAuthorized.start();
    }

    private static void callServer(Server server, boolean auth) {
        String result = server.serve(new Request(auth));
        System.out.println( "thread " + Thread.currentThread().getName() + " got result " + result );
    }

    static class Server {
        final static ThreadLocal<Principal> PRINCIPAL = new ThreadLocal<>();

        String serve(Request request) {
            var level = request.authorized() ? RightsLevel.ADMIN : RightsLevel.GUEST;
            var principal = new Principal(level);
            PRINCIPAL.set(principal);
            return Application.handle(request);
        }
    }

    static class Application {
        public static String handle(Request request) {
            return DBConnection.open().doQuery();
        }
    }

    static class DBConnection {
        static DBConnection open() {
            var principal = Server.PRINCIPAL.get();
            if (!principal.canOpen()) throw new IllegalArgumentException();
            return new DBConnection();
        }

        String doQuery() {
            return "Result";
        }
    }

    enum RightsLevel {
        ADMIN, GUEST
    }

    record Principal(RightsLevel rights) {
        public boolean canOpen() {
            return rights == RightsLevel.ADMIN;
        }
    }

    record Request(boolean authorized) { }
}
