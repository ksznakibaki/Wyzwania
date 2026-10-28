package pl.filip.wyzwania;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static pl.filip.wyzwania.Model.*;

class DefaultConfigTest {
    @Test void shippedYamlHasValidPoolsAndCanGenerateEveryDayWithoutPvp() {
        LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false);
        Map<String, Object> config = new Yaml(new SafeConstructor(options)).load(getClass().getResourceAsStream("/config.yml"));
        assertEquals("Europe/Warsaw", map(config.get("reset")).get("timezone"));
        Map<String, Object> pools = map(config.get("rewards"));
        for (Tier tier : Tier.values()) {
            Map<String, Object> pool = map(pools.get(tier.name()));
            assertTrue((int) pool.get("xp-min") <= (int) pool.get("xp-max"));
            assertFalse(map(pool.get("pool")).isEmpty());
            assertEquals(tier == Tier.WEEKLY ? 50 : 10, pool.get("points"));
            for (Object prize : map(pool.get("pool")).values()) {
                assertTrue((int) map(prize).get("weight") > 0);
                for (Object item : (List<?>) map(prize).get("items")) {
                    assertFalse(map(item).get("material").toString().isBlank());
                    assertTrue((int) map(item).get("amount") > 0);
                }
            }
        }
        List<Template> templates = new ArrayList<>();
        map(config.get("quests")).forEach((id, value) -> {
            Map<String, Object> quest = map(value);
            List<Objective> objectives = new ArrayList<>();
            for (Object raw : (List<?>) quest.get("objectives")) {
                var o = map(raw);
                objectives.add(new Objective(Type.valueOf(o.get("type").toString()), o.get("target").toString(),
                        ((Number) o.get("goal")).doubleValue(), o.get("label").toString()));
            }
            if (objectives.stream().noneMatch(o -> o.type() == Type.PVP_DAMAGE || o.type() == Type.PVP_KILL))
                templates.add(new Template(id, Tier.valueOf(quest.get("tier").toString()), quest.get("title").toString(), objectives));
        });
        QuestEngine engine = new QuestEngine(new State(), QuestEngineTest.PERIODS, templates, QuestEngineTest::reward, new Random(1));
        for (int day = 0; day < 365; day++) {
            Set<String> previous = new HashSet<>();
            engine.state.daily.forEach(q -> previous.add(QuestEngine.templateId(q)));
            engine.rotate(QuestEngineTest.MONDAY.plusSeconds(day * 86400L));
            assertEquals(3, engine.state.daily.size()); assertEquals(1, engine.state.weekly.size());
            assertEquals(3, engine.state.daily.stream().map(q -> q.objectives().getFirst().type()).distinct().count());
            assertTrue(engine.state.daily.stream().noneMatch(q -> previous.contains(QuestEngine.templateId(q))));
        }
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
}
