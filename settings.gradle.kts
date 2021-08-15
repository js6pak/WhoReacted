rootProject.name = "aliucord-plugins"

include(":WhoReacted")
include(":ValidUser")

include(":Aliucord")
project(":Aliucord").projectDir = File("../repo/Aliucord")