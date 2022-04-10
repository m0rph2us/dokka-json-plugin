package morpheus

import junit.framework.Assert.assertNotNull
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.junit.Test

class JsonDokkaPluginTest : BaseAbstractTest() {
    private val configuration = dokkaConfiguration {
        sourceSets {
            sourceSet {
                sourceRoots = listOf("src/main/kotlin")
            }
        }
    }

    @Test
    fun `my awesome plugin should find packages and classes`() {
        testInline(
            """
            |/src/main/kotlin/sample/Test.kt
            |package sample
            |/**
            |* TEST
            |*/
            |data class TestingIsEasy(
            |/**
            |* test
            |*/
            |val reason: String
            |)
            """.trimIndent(), configuration
        ) {
            documentablesTransformationStage = { module ->
                val testedPackage = module.packages.find { it.name == "sample" }
                val testedClass = testedPackage?.classlikes?.find { it.name == "TestingIsEasy" }

                assertNotNull(testedPackage)
                assertNotNull(testedClass)
            }
        }
    }

    @Test
    fun `should generate json output`() {
        testInline(
            """
            /src/main/kotlin/sample/TestDokka.kt
            package sample.testdata

            import java.math.BigDecimal

            /**
             * Class documentation
             *
             * Next paragraph
             * UTF 8 test: 我能吞下玻璃而不伤身体。Árvíztűrő tükörfúrógép
             *
             * * Item 1
             * * Item 2
             */
            data class KotlinDataClass(
                    /**
                     * Documentation for _text_ property (テスト)
                     *
                     * @see KotlinDataClass
                     * @deprecated Use something else (テスト)
                     * @title Custom tag
                     */
                    val text: String,
                    /**
                     * Documentation for *number* property
                     */
                    val number: BigDecimal) {
            
                /**
                 * Function add (テスト)
                 *
                 * [some link](http://some-url.com)
                 *
                 * @param a First param **a**
                 * @param b Second param __b__
                 * @see KotlinDataClass
                 * @deprecated Use something else
                 * @title Custom tag
                 */
                fun add(a: BigDecimal, b: BigDecimal): BigDecimal = a + b
            
                /**
                 * A nested class
                 *
                 * ``There is a literal backtick (`) here.``
                 */
                data class NestedClass(
                        /**
                         * Field on a nested class with some code: `println("foo")`
                         */
                        val someField: String)
            }
            """.trimIndent(), configuration
        ) {
            documentablesTransformationStage = { module ->
            }
        }
    }
}